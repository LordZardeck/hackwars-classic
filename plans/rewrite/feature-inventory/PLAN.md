# Rewrite Feature Inventory

## Inventory Rules
- Every retained feature row must list a foundation task, a completion task, a legacy UI reference, a parity acceptance target, and a blocking upstream task when the row is not `ready` or `in_progress`.
- No retained feature row may be `done` until both its foundation task and its completion task are `done`.
- `Parity acceptance` means a named deterministic screenshot baseline or equivalent workflow artifact, not a general statement that the feature “looks close enough.”
- Hacktendo and Command Prompt rows are removed from retained rewrite scope.

## Feature Row Template
| Feature | Foundation task | Completion task | Legacy UI reference | Parity acceptance | Blocking upstream task | Evidence | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |

## Auth, Session, and Shell
| Feature | Foundation task | Completion task | Legacy UI reference | Parity acceptance | Blocking upstream task | Evidence | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Login and desktop entry | `RW-CLIENT-002` | `RW-CLIENT-C0C` | `LoginScene`, `LoginForm`, `LoginBackgroundPanel` | `login_scene_main.png`, `login_scene_error.png`, deterministic login workflow artifact | `none` | `LoginFormTest`, `LoginBackgroundPanelTest`, `RW-TEST-007` | `in_progress` |
| Desktop shell chrome and taskbar | `RW-CLIENT-003A`, `RW-CLIENT-003B1`, `RW-CLIENT-003B2` | `RW-CLIENT-C1C` | `Hacker`, `StatsPanel`, `StatIcon`, `MoneyIcon`, `BarPanel`, `LevelPanel`, `CPULoadIcon` | `shell_main.png`, `shell_taskbar.png`, `shell_messages.png` | `none` | `RewriteDesktopShellTest`, `RewriteRootFrameUiTest`, `RW-TEST-007` | `in_progress` |
| Stats rail and identity bar | `RW-CLIENT-003B1` | `RW-CLIENT-C1C` | `StatsPanel`, `StatIcon`, `MoneyIcon`, `BarPanel`, `LevelPanel`, `CPULoadIcon` | `shell_stats_panel.png`, `shell_cpu_bar.png`, real IPv4 shown in deterministic fixture | `none` | `RewriteRootFrameUiTest`, `RW-TEST-007` | `in_progress` |

## Economy, Files, FTP, and Web
| Feature | Foundation task | Completion task | Legacy UI reference | Parity acceptance | Blocking upstream task | Evidence | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Banking windows | `RW-CLIENT-W1A` | `RW-CLIENT-C2C` | `Deposit`, `Withdraw`, `Transfer`, `MoneyIcon` | `bank_deposit.png`, `bank_withdraw.png`, `bank_transfer.png` | `none` | `RewriteBankingUiTest`, `RW-TEST-009` | `in_progress` |
| Bounty flow | `RW-CLIENT-W1B` | `RW-CLIENT-C2C` | `BountyWindow`, `BountyFileChooser` | `bounty_window.png`, `bounty_chooser.png` | `none` | `RewriteBountyUiTest`, `RW-TEST-009` | `in_progress` |
| Home file browser and local chooser | `RW-CLIENT-W2A` | `RW-CLIENT-C3C` | `Home`, `HomeList`, `HomeIcon`, `HomeCellRenderer`, `HomeTableCellRenderer` | `home_root.png`, `home_directory.png`, `home_chooser.png` | `none` | `RewriteHomeWindowUiTest`, `RW-TEST-009` | `in_progress` |
| File properties | `RW-CLIENT-W2B1` | `RW-CLIENT-C3C` | `FileProperties` | `file_properties.png` | `none` | `RewriteFileViewerUiTest`, `RW-TEST-009` | `in_progress` |
| Script editor | `RW-CLIENT-W2B2` | `RW-CLIENT-C3C` | `ScriptEditor`, `ScriptEditorPane`, `ScriptInternalFunctionPane` | `script_editor_main.png`, `script_editor_save.png` | `none` | `RewriteScriptEditorUiTest`, `RW-TEST-009` | `in_progress` |
| Image and binary viewer | `RW-CLIENT-W2B3` | `RW-CLIENT-C3C` | `ImageViewer`, `ImageViewerPanel` | `image_viewer.png`, `binary_viewer.png` | `none` | `RewriteFileViewerUiTest`, `RW-TEST-009` | `in_progress` |
| Shop FTP seller surface | `RW-CLIENT-W2C1` | `RW-CLIENT-C4C` | `FTP`, `FTPMouseListener`, `FTPCellRenderer`, `FTPQuantityDialog` | `shop_ftp_main.png`, `shop_ftp_sell_dialog.png` | `none` | `RewriteFtpWindowsUiTest`, `RW-TEST-009` | `in_progress` |
| Public FTP browser | `RW-CLIENT-W2C1` | `RW-CLIENT-C4C` | `FTP`, `FTPMouseListener`, `FTPCellRenderer` | `public_ftp_connect.png`, `public_ftp_directory.png` | `none` | `RewriteFtpWindowsUiTest`, `RW-TEST-009` | `in_progress` |
| FTP transfer and public-FTP password flows | `RW-CLIENT-W2C2` | `RW-CLIENT-C4C` | `FTP`, `FTPQuantityDialog`, retained password affordance from legacy FTP flow | `ftp_transfer.png`, `ftp_password_dialog.png` | `RW-GS-T2` | `RW-TEST-009` | `blocked` |
| Web Browser and Store | `RW-CLIENT-W3A` | `RW-CLIENT-C5C` | `WebBrowser`, `StoreActionListener` | `web_browser.png`, `store_listing.png`, `store_purchase.png` | `none` | `RewriteWebBrowserUiTest`, `RW-TEST-009` | `in_progress` |
| Website Editor | `RW-CLIENT-W3B` | `RW-CLIENT-C5C` | `WebsiteEditor` | `website_editor_site.png`, `website_editor_preview.png`, `website_editor_insert_link.png` | `none` | `RewriteSiteEditorUiTest`, `RW-TEST-009` | `in_progress` |
| Help | `RW-CLIENT-W3C2` | `RW-CLIENT-C5C` | `Help`, `HelpFile` | `help_main.png`, `help_topic.png` | `RW-GS-T5` | `RW-TEST-011` | `blocked` |
| Tutorial | `RW-CLIENT-W3C2` | `RW-CLIENT-C5C` | `TutorialWindow`, `Tutorial` | `tutorial_window.png`, `tutorial_progression.png` | `RW-GS-T5` | `RW-TEST-011` | `blocked` |

## Systems, Network, and Combat
| Feature | Foundation task | Completion task | Legacy UI reference | Parity acceptance | Blocking upstream task | Evidence | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Network map and switching | `RW-CLIENT-W5A` | `RW-CLIENT-C6C` | `MapPanel`, `NetworkPanel`, `NetworkInfoPanel`, `NetworkMapPanel` | `network_main.png`, `network_map.png`, `network_switch.png` | `none` | `RewriteNetworkWindowsUiTest`, `RW-TEST-008` | `in_progress` |
| Port Scan | `RW-CLIENT-W5A` | `RW-CLIENT-C6C` | `PortScan`, `PortScanTableModel`, `PortScanTableCellRenderer` | `port_scan_entry.png`, `port_scan_results.png` | `none` | `RewriteNetworkWindowsUiTest`, `RW-TEST-008` | `in_progress` |
| Port Management | `RW-CLIENT-W4A` | `RW-CLIENT-C7C` | `PortManagement`, `PortManagementMouseListener`, `PortManagementOnOffActionListener`, `PortManagementDefaultListener` | `port_management_tabs.png`, `port_management_status.png` | `none` | `RewritePortManagementUiTest`, `RW-TEST-008` | `in_progress` |
| Equipment Manager | `RW-CLIENT-W4B` | `RW-CLIENT-C8C` | `Equipment`, `EquipmentPopUp` | `equipment_manager.png` | `none` | `RewriteInventoryManagersUiTest`, `RW-TEST-008` | `in_progress` |
| Firewall Manager | `RW-CLIENT-W4B` | `RW-CLIENT-C8C` | `FirewallBrowser`, `FirewallQuantityDialog` | `firewall_manager.png` | `none` | `RewriteInventoryManagersUiTest`, `RW-TEST-008` | `in_progress` |
| Watch Manager | `RW-CLIENT-W4C` | `RW-CLIENT-C8C` | `WatchManager`, `WatchQuantityDialog`, `WatchInstallFileChooser` | `watch_manager.png`, `watch_observed_ports.png` | `none` | `RewriteWatchManagerUiTest`, `RW-TEST-008` | `in_progress` |
| Attack and Redirect panes | `RW-CLIENT-W5B1` | `RW-CLIENT-C9C` | `AttackPane`, `AttackDialog`, `AttackFileChooser`, `AttackAnimator` | `attack_pane.png`, `redirect_pane.png`, `attack_file_chooser.png` | `RW-GS-T6`, `RW-GS-T7` | `RewriteAttackWindowsUiTest`, `RW-TEST-010` | `in_progress` |
| Choices follow-up and remote secondary directory browser | `RW-CLIENT-W5B2` | `RW-CLIENT-C9C` | `ChoicesProgressBar`, `ShowChoicesFileChooser`, `FTP` | `choices_window.png`, `choices_public_ftp.png`, `choices_store_ftp.png` | `RW-GS-T7` | `RewriteAttackWindowsUiTest`, `RW-TEST-010` | `in_progress` |
| Zombie Attack | `RW-CLIENT-W5C` | `RW-CLIENT-C9C` | `ZombieAttackDialog`, `ZombieAttackPane` | `zombie_attack_dialog.png`, `zombie_attack_runtime.png` | `RW-GS-T7` | `RewriteZombieAttackWindowsUiTest`, `RW-TEST-010` | `in_progress` |

## Utilities and Chat
| Feature | Foundation task | Completion task | Legacy UI reference | Parity acceptance | Blocking upstream task | Evidence | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Preferences | `RW-CLIENT-W7A` | `RW-CLIENT-C10C` | `OptionPanel` | `preferences_main.png`, `preferences_apply_error.png` | `none` | `RewriteUtilitiesUiTest`, `RW-TEST-011` | `in_progress` |
| Log Window | `RW-CLIENT-W7A` | `RW-CLIENT-C10C` | `LogWindow` | `log_window.png` | `none` | `RewriteUtilitiesUiTest`, `RW-TEST-011` | `in_progress` |
| Personal Settings | `RW-CLIENT-W7B` | `RW-CLIENT-C10C` | `PersonalSettings` | `personal_settings_main.png` | `RW-GS-T4` | `RW-TEST-011` | `blocked` |
| Client chat shell and dialogs | `RW-CLIENT-W6` | `RW-CLIENT-C11C` | `Messager`, `MessageWindow`, `ChatResizeLine` | `client_chat_shell.png`, `client_chat_message_window.png` | `RW-CHAT-003B`, `RW-CHAT-004B`, `RW-CHAT-005A` | `RW-TEST-011`, `RW-CHAT-005B` | `blocked` |
