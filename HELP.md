# Help

Task-oriented usage for Commit Generator.

## 1. Install and open

1. Install the plugin from **Settings | Plugins | Marketplace**, or install the ZIP from disk.
2. Restart the IDE.
3. Open a Git repository.
4. Open the **Commit Generator** tool window on the right side. If it is hidden, use
   **View | Tool Windows | Commit Generator** or run **Commit Generator: Open Commit Generator** from
   **Find Action**.

## 2. Add a provider

1. In the tool window, click **Add** under **Provider**.
2. Choose a provider preset.
3. Enter a display label and model id. Click **Use preset defaults** to fill the endpoint.
4. Enter the API key or password. It is stored in the OS keychain.
5. Click **Save**.
6. Click **Test** to verify the endpoint, key, and model list.

For a local model:

1. Start Ollama, LM Studio, llama.cpp, vLLM, or OpenCode.
2. Add the matching preset.
3. Keep the default loopback URL and port.
4. Use **Select Model** to load the local model list.
5. Enable **Local-only mode**.

## 3. Choose what to analyse

The three toggles are independent:

- **Staged changes** reads the Git index (`HEAD` to index).
- **Unstaged changes** reads the working tree, including merge conflicts.
- **Untracked files** reads files Git does not track yet.

In classic JetBrains commit workflows, changes are not staged first. Either:

- enable the Git staging area in **Settings | Version Control | Git**, or
- run `git add` in a terminal, or
- enable **Unstaged changes** in Commit Generator.

If a path has both staged and unstaged edits and both toggles are enabled, Commit Generator asks
Git for the combined `HEAD` diff, so the message describes the whole change.

## 4. Generate

From the tool window:

1. Choose a mode: Quick, Smart, or Detailed.
2. Press **Generate commit message**.
3. On first use, approve the network consent dialog.
4. Wait for the message. You can cancel at any time.

From the Commit panel:

1. Open the **Commit** tool window.
2. Click the Commit Generator icon in the commit message toolbar.
3. The generated message is written into the field.

If Commit Generator cannot find the commit field (for example, the Commit tool window is closed),
the message stays in the Commit Generator tool window with **Insert** and **Copy** actions. Open
the Commit tool window and press **Insert**.

## 5. Review and commit

- Review the subject, body, and footers.
- Edit the message directly in the commit field.
- Use **Undo** to restore the previous draft.
- Use **Regenerate** to retry with the same change snapshot.
- Commit yourself when ready. Commit Generator never commits.

## 6. Preview the payload

Run **Preview Payload To Be Sent** or press **Preview** in the tool window. The dialog shows
the redacted system and user prompt, estimated tokens, file count, and omitted files. No
network request is made.

## 7. Manage drafts

The **Existing draft** setting controls what happens when the commit field already contains
text:

- **ask** - choose Replace, Append, or Cancel.
- **replace** - overwrite the draft.
- **append** - add the generated message below the draft.

Undo restores the text that was present before the write.

## 8. Troubleshooting

### "No staged changes"

Stage files with `git add`, enable the Git staging area, or enable **Unstaged changes**.

### The message is generated but not inserted

The Commit tool window is probably closed or not initialized. Open it, then press **Insert**.
The message stays in the Commit Generator tool window and is never written to the clipboard unless
you press **Copy**.

### The action is disabled

- No provider profile: add one.
- Generation already running: wait or cancel.
- No open Git repository: open one.

### Authentication errors

Re-enter the API key for the active profile. Use **Test** to verify.

### The model is not listed

Some endpoints do not expose a model list. Choose **Select Model** and enter the model id
manually.

### Local server refuses the connection

- Start the server.
- Check its port.
- Confirm the base URL uses loopback.
- Enable Local-only mode for safety.

### The message is generic

- Use Detailed mode.
- Stage fewer files.
- Increase the diff token budget.
- Add an issue key or use a more capable model.

### The payload is too large

- Lower **Max files**.
- Lower **Max diff tokens**.
- Add exclusion globs.
- Use Smart/Quick mode.

### Proxy required

Version 1 does not read IDE proxy settings. Use a local model or a reachable endpoint.

## 9. Get more information

- **Show Diagnostics** reports IDE build, Git repositories, profiles, mode, and consent count.
- **Details** on the result reports warnings, omitted files, coverage, and usage.
- The IDE log contains metadata only, never prompts or diffs.
