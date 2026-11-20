# Specs

One folder per spec: `specs/<ID>-<slug>/` with `spec.md`, `security.md`, `test-cases.md` and `gate.md`.
The ID is the tracker issue or ticket key. Create packages with the `spec-create` skill or
`python sdd/tools/sdd.py spec new <ID> "<Title>"`; never by hand from memory.

The workflow is in [sdd/workflow.md](../sdd/workflow.md). Validate with
`python sdd/tools/sdd.py spec validate --all`.

`specs/pendientes/` is not a package: it holds the critical-path gaps (`RC-NN`) assigned to stories that do
not have a spec yet. `spec-create` reads the story's note and turns each gap into a `DERIVED` criterion.
