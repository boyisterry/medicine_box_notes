# Gemma 4 E2B local model cache

This directory is the local, resumable cache for the Android on-device model.
The model binary is intentionally ignored by Git.

- Hugging Face repository: `litert-community/gemma-4-E2B-it-litert-lm`
- Local filename: `gemma-4-E2B-it.litertlm`
- Android private destination: `files/models/gemma-4-E2B-it.litertlm`
- Required SHA-256: `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`

Download with resume support:

```sh
curl -L --fail --retry 5 -C - \
  -o models/gemma-4-e2b/gemma-4-E2B-it.litertlm.part \
  https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm
```

After checksum verification, rename the `.part` file to `gemma-4-E2B-it.litertlm`.
