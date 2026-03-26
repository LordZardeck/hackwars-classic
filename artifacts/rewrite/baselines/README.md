Rewrite screenshot baseline artifacts live under this directory.

Workflow layout:
- `pass-2/<feature>/<artifact>.png`
- `pass-2/<feature>/<artifact>.json`
- `pass-3/<feature>/<artifact>.png`
- `pass-3/<feature>/<artifact>.json`
- and so on for the retained parity packs

Each approved baseline JSON manifest records:
- the feature key and artifact filename
- the originating suite and test
- the task/pass identifier
- the legacy UI reference
- the parity-acceptance path that the feature inventory points at
- the capture dimensions and environment metadata

Only the scaffold files in this directory are tracked. Generated baseline images and approval manifests stay ignored until the parity packs populate them.
