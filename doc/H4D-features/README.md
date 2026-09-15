# H4D Features — Index

Small, single-topic files. Read only the ones relevant to the current task — do not bulk-read this folder.

| File | Read this when... |
|---|---|
| [upstream-merge-workflow.md](upstream-merge-workflow.md) | Pulling in a new NightDawg release / resolving merge conflicts with `upstream`. |
| [release-process.md](release-process.md) | Cutting a release of *this* fork for players (the `Create Release` Action, why `Release/` is stale/legacy). |
| [menugrid-system.md](menugrid-system.md) | Adding/changing anything in the bottom-right "Custom Client Extras" grid menu (new toggle, new bot, new script button). |
| [res-file-binary-format.md](res-file-binary-format.md) | Creating or editing a `.res` file by hand (icon/action/tooltip resource for the menu grid). |
| [chat-message-injection.md](chat-message-injection.md) | Sending a chat message from code without touching keyboard focus/UI (bots, scripts, alerts). |
| [yapper-bot.md](yapper-bot.md) | Working on the Yapper Bot feature specifically. |
| [group-permission-extension.md](group-permission-extension.md) | Touching `BuddyWnd.GroupSelector`, Kin/Village/claim/Field-Cairn permission groups, or `haven.groups.*`. |

Tools: [tools/New-MenuGridButton.ps1](tools/New-MenuGridButton.ps1) — generates a menu-grid `.res` file by splicing a name/tooltip/description into an existing button's icon, without needing the (unavailable) official resource editor.
