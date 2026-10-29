"""Servicio de autenticación, control de intentos y gestión de sesiones (HU-WEB-13)."""
from datetime import datetime, timedelta, timezone
from typing import Optional
from fastapi import HTTPException, status
import jwt
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.security import (
    create_access_token,
    create_refresh_token,
    decode_refresh_token,
    get_password_hash,
    verify_password,
    verify_totp,
)
from app.modules.rating.models import PartnerDB
from app.modules.auth.models import LoginResponse, UserDB

DEFAULT_SEED_USERNAME = "asesor@solventa.com"
DEFAULT_SEED_PASSWORD = "SuperSecreta2026*"
DEFAULT_SEED_NAME = "Carlos Asesor"
# Analistas de operaciones de prueba (HU-WEB-08): uno ve todas las pólizas y el otro solo las de Banco Aliado.
SEED_OPERATIONS_USERS = [
    ("operaciones@solventa.example", "Analista de Operaciones", None),
    ("analista@bancoaliado.example", "Analista Banco Aliado", "banco-aliado-1"),
]
DEFAULT_SEED_TOTP_SECRET = "JBSWY3DPEHPK3PXP"


class AuthService:
    """Servicio de dominio para autenticación 2FA y gestión de sesiones de usuario."""

    async def get_user_by_username(
        self, session: AsyncSession, username: str
    ) -> Optional[UserDB]:
        """Consulta un usuario por su nombre de usuario (email corporativo)."""
        clean_username = username.strip().lower()
        stmt = select(UserDB).where(UserDB.username == clean_username)
        result = await session.execute(stmt)
        return result.scalars().first()

    async def create_user(
        self,
        session: AsyncSession,
        username: str,
        password: str,
        full_name: str,
        role: str = "advisor",
        totp_secret: str = DEFAULT_SEED_TOTP_SECRET,
        partner_id: Optional[str] = None,
    ) -> UserDB:
        """Registra un nuevo usuario con contraseña hasheada y secreto TOTP."""
        return await self._add_user(
            session, username, get_password_hash(password), full_name, role, totp_secret, partner_id
        )

    async def _add_user(
        self,
        session: AsyncSession,
        username: str,
        hashed_password: str,
        full_name: str,
        role: str,
        totp_secret: str,
        partner_id: Optional[str],
    ) -> UserDB:
        user = UserDB(
            username=username.strip().lower(),
            hashed_password=hashed_password,
            totp_secret=totp_secret.strip().upper(),
            role=role,
            partner_id=partner_id,
            full_name=full_name.strip(),
            is_active=True,
            failed_attempts=0,
            locked_until=None,
        )
        session.add(user)
        await session.commit()
        await session.refresh(user)
        return user

    async def seed_default_users(self, session: AsyncSession) -> UserDB:
        """Crea el asesor y los analistas de operaciones semilla si aún no existen (HU-WEB-13, HU-WEB-08).
        Los analistas usan la misma credencial del asesor; solo se siembran fuera de producción."""
        advisor = await self.get_user_by_username(session, DEFAULT_SEED_USERNAME)
        if advisor is None:
            advisor = await self.create_user(
                session=session,
                username=DEFAULT_SEED_USERNAME,
                password=DEFAULT_SEED_PASSWORD,
                full_name=DEFAULT_SEED_NAME,
                role="advisor",
                totp_secret=DEFAULT_SEED_TOTP_SECRET,
            )
        await self._seed_operations_users(session, advisor.hashed_password)
        return advisor

    async def _seed_operations_users(self, session: AsyncSession, hashed_password: str) -> None:
        for username, full_name, partner_id in SEED_OPERATIONS_USERS:
            if await self.get_user_by_username(session, username):
                continue
            if partner_id:
                found = await session.execute(select(PartnerDB).where(PartnerDB.partner_id == partner_id))
                if found.scalars().first() is None:
                    continue
            await self._add_user(
                session, username, hashed_password, full_name, "analyst", DEFAULT_SEED_TOTP_SECRET, partner_id
            )

    async def authenticate_user(
        self,
        session: AsyncSession,
        username: str,
        password: str,
        totp_code: str,
    ) -> UserDB:
        """
        Valida credenciales y código 2FA TOTP con política de bloqueo:
        - Si la cuenta acumula 5 intentos fallidos consecutivos, se bloquea por 15 minutos (HTTP 423).
        - Si las credenciales o código 2FA son incorrectos, retorna mensaje unificado (HTTP 401).
        - Si es exitoso, resetea el contador de intentos fallidos.
        """
        user = await self.get_user_by_username(session, username)
        

        # Mensaje de error unificado de seguridad
        invalid_credentials_exc = HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Credenciales o código de verificación inválidos",
        )

        if not user or not user.is_active:
            raise invalid_credentials_exc

        now = datetime.now(timezone.utc)

        # 1. Verificar si la cuenta se encuentra bloqueada
        if user.locked_until:
            # Asegurar compatibilidad de zonas horarias en la comparación
            locked_until = user.locked_until
            if locked_until.tzinfo is None:
                locked_until = locked_until.replace(tzinfo=timezone.utc)

            if locked_until > now:
                raise HTTPException(
                    status_code=status.HTTP_423_LOCKED,
                    detail="Cuenta bloqueada por seguridad debido a múltiples intentos fallidos. Intente nuevamente en 15 minutos.",
                )
            else:
                # El período de bloqueo ya expiró: restablecer estado
                user.locked_until = None
                user.failed_attempts = 0

        # 2. Validar contraseña y segundo factor TOTP (HA-06)
        password_valid = verify_password(password, user.hashed_password)
        totp_valid = verify_totp(user.totp_secret, totp_code, tolerance_steps=1)

        if not (password_valid and totp_valid):
            user.failed_attempts += 1
            if user.failed_attempts >= settings.MAX_FAILED_LOGIN_ATTEMPTS:
                user.locked_until = now + timedelta(
                    minutes=settings.LOCKOUT_DURATION_MINUTES
                )
                await session.commit()
                raise HTTPException(
                    status_code=status.HTTP_423_LOCKED,
                    detail="Cuenta bloqueada por seguridad debido a múltiples intentos fallidos. Intente nuevamente en 15 minutos.",
                )

            await session.commit()
            raise invalid_credentials_exc

        # 3. Autenticación exitosa: restablecer contador de fallos
        user.failed_attempts = 0
        user.locked_until = None
        await session.commit()
        return user

    def generate_session_tokens(self, user: UserDB) -> LoginResponse:
        """Genera tokens de acceso y de refresco para el usuario autenticado."""
        access_token = create_access_token(
            subject=user.username,
            role=user.role,
            claims={"advisor_name": user.full_name},
        )
        refresh_token = create_refresh_token(
            subject=user.username,
            role=user.role,
        )
        return LoginResponse(
            access_token=access_token,
            refresh_token=refresh_token,
            token_type="bearer",
            role=user.role,
            advisor_name=user.full_name,
            expires_in=settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60,
        )

    async def refresh_session(
        self,
        session: AsyncSession,
        refresh_token: str,
    ) -> LoginResponse:
        """Valida token de refresco y renueva la sesión silenciosamente."""
        try:
            payload = decode_refresh_token(refresh_token)
        except (jwt.ExpiredSignatureError, jwt.InvalidTokenError):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Token de refresco inválido o expirado",
            )

        username = payload.get("sub")
        if not username:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Token de refresco inválido",
            )

        user = await self.get_user_by_username(session, username)
        if not user or not user.is_active:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Usuario inactivo o no encontrado",
            )

        return self.generate_session_tokens(user)


auth_service = AuthService()

