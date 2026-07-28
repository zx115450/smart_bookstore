# IDEA + Git 工作流基础

> **项目**：smart_bookstore（智慧书城）  
> **文档类型**：学习文档 · 工程基础（弱耦合，建议结合项目环境练习）  
> **关联包 / 模块**：`—（工程基础，不绑定业务包）`  
> **建议前置**：无；建议在克隆本仓库后边做边看

## 学习目标

1. 掌握在 IDEA 中完成 clone、分支、提交与推送
2. 理解 diff、stash、merge conflict 的日常处理
3. 能用 Git 管理本仓库的本地改动

## 与本仓库的对应关系

| 路径 | 说明 |
| --- | --- |
| — | 本文为工程基础，无强制代码路径 |

读理论时请打开上表文件对照；改代码前先跑通 `docker compose up -d` 与 `local` profile。

---

## 目录

1. [写在前面](#写在前面)
2. [使用前准备](#一使用前准备)
3. [把项目接上 Git](#二把项目接上-git)
4. [认文件颜色与 Diff](#三认文件颜色与-diff)
5. [日常主流程：Commit → Push → Update](#四日常主流程commit--push--update)
6. [分支](#五分支)
7. [合并与冲突](#六合并与冲突)
8. [查看历史](#七查看历史)
9. [分享到 GitHub](#八分享到-github可选)
10. [实用建议与速查表](#九实用建议与速查表)
11. [小结](#十小结)

---

## 导读
很多同学学 Git 从命令行开始：`add` / `commit` / `push` / `pull` / `checkout`。  
在 IDEA 里这些都有图形入口，并且多了三样命令行不好「一眼看到」的能力：

1. **文件颜色与 Gutter 变更条** — 看出改了哪一行  
2. **Commit 工具窗口** — 勾选文件、写说明、看 Diff  
3. **三方合并界面** — 冲突时左 / 中 / 右对照解决  

心智模型一句话：

> IDEA 不替代 Git，只是把 Git 可视化；底层仍是本机安装的 `git` 可执行文件。

官方英文教程：[Working with Git in IntelliJ IDEA](https://www.jetbrains.com/help/idea/working-with-git-tutorial.html)

---

## 一、使用前准备

### 1.1 安装 Git

| 环境 | 做法 |
|------|------|
| Windows | 安装 [Git for Windows](https://git-scm.com/)，勾选「加入 PATH」 |
| macOS | `xcode-select --install`，或用 Homebrew 安装 Git |
| IDEA | 检测不到 Git 时会提示下载 |

### 1.2 确认 IDEA 能找到 Git

路径：`Settings`（macOS：`Preferences`）→ **Version Control** → **Git** → *Path to Git executable* → **Test**。

### 1.3 常用快捷键（Windows / Linux）

| 操作 | 快捷键 |
|------|--------|
| Commit 工具窗口 | `Alt+0` 或 `Ctrl+K` |
| Commit | `Ctrl+K` |
| Commit and Push | `Ctrl+Alt+K` |
| Push | `Ctrl+Shift+K` |
| Update Project | `Ctrl+T` |
| Git 工具窗口 / Log | `Alt+9` |
| Diff | `Ctrl+D` |
| Add to VCS | `Ctrl+Alt+A` |

> 也可使用顶栏 **Git** 菜单；macOS 把 `Ctrl` 换成 `⌘` 即可对照。

---

## 二、把项目接上 Git

### 2.1 新建项目时创建仓库

欢迎页 → **New Project** → 勾选 **Create Git repository** → Create。

![新建项目并勾选 Create Git repository](https://resources.jetbrains.com/help/img/idea/2026.2/gitdemo_empty_project.png)

*图：新建空项目并创建本地 Git 仓库*

创建成功后会出现 VCS 相关入口（分支条、Commit、Git 工具窗口）：

![Git 集成已启用](https://resources.jetbrains.com/help/img/idea/2026.2/git_integration_enabled.png)

*图：本地仓库创建成功的提示*

### 2.2 已有项目开启版本控制

**VCS → Enable Version Control Integration…** → 选择 **Git** → OK  

≈ 在项目根目录执行 `git init`，再由 IDEA 关联。

### 2.3 Clone 远程仓库

欢迎页 **Get from VCS**，或 **Git → Clone…**，填入 HTTPS / SSH 地址。  

适合直接拉 GitHub / Gitee / GitLab 上的项目。

---

## 三、认文件颜色与 Diff

开启 Git 后，Project 视图与编辑器 Tab 会用颜色提示状态：

| 颜色 | 含义 |
|------|------|
| 绿色 | 新文件（已跟踪、尚未提交，或刚 Add） |
| 蓝色 | 已修改 |
| 灰色 | 已删除（待提交） |
| 红 / 棕等 | 冲突或特殊状态（随主题略有差异） |

编辑器左侧 **Gutter**（行号旁竖条）可点开查看相对上次提交的行级差异：

![Gutter 变更标记](https://resources.jetbrains.com/help/img/idea/2026.2/change_marker.png)

*图：行号旁的变更标记*

![选中行的 Diff](https://resources.jetbrains.com/help/img/idea/2026.2/diff_for_range.png)

*图：查看选中行差异（Show Diff for Lines）*

---

## 四、日常主流程：Commit → Push → Update

### 4.1 Add（加入版本控制）

新建文件时，IDEA 常会询问是否加入 Git：

![添加文件到 Git](https://resources.jetbrains.com/help/img/idea/2026.2/add_file_to_git.png)

*图：是否将新文件加入版本控制*

错过对话框时：Project 中右键文件 → **Git → Add**（或 `Ctrl+Alt+A`）。  
加入后会出现在 Commit 工具窗口：

![变更列表中的新文件](https://resources.jetbrains.com/help/img/idea/2026.2/file_added_to_git.png)

*图：文件已进入 Changes 列表*

### 4.2 Commit（提交到本地）

1. 打开 **Commit** 窗口（`Alt+0` / `Ctrl+K`）  
2. 勾选要提交的文件  
3. 写提交说明（优先写「为什么」，少写流水账）  
4. 点 **Commit**，或 **Commit and Push**

![填写 Commit Message](https://resources.jetbrains.com/help/img/idea/2026.2/commit_message.png)

*图：勾选文件并填写提交说明*

提交说明支持文件名补全（`Ctrl+Space`）：

![Commit 消息补全](https://resources.jetbrains.com/help/img/idea/2026.2/commit_completion.png)

*图：提交说明自动补全*

提交成功后可收到通知；双击文件可看完整 Diff：

![本地变更 Diff 预览](https://resources.jetbrains.com/help/img/idea/2026.2/local_changes.png)

*图：Commit 窗口中的 Diff 预览*

对应命令行：

```bash
git add <files>
git commit -m "your message"
```

### 4.3 Push（推到远程）

**Git → Push**，或 `Ctrl+Shift+K`：

![Push Commits 对话框](https://resources.jetbrains.com/help/img/idea/2026.2/push_commits.png)

*图：确认要推送的提交与文件*

对应命令行：`git push`  

> 第一次推新分支时，IDEA 会提示设置 upstream（类似 `git push -u origin feature-xxx`）。

### 4.4 Update / Pull（同步远程）

**Git → Update Project…**，或 `Ctrl+T`：

![Update Project](https://resources.jetbrains.com/help/img/idea/2026.2/git-update-project-popup.png)

*图：Update Project（Merge / Rebase）*

| 策略 | 含义 | 近似命令 |
|------|------|----------|
| Merge | 拉远程后做合并提交 | `git pull --no-rebase` |
| Rebase | 本地提交接到远程最新之后 | `git pull --rebase` |

团队约定一种即可；个人小项目用 Merge 通常更省心。

---

## 五、分支

### 5.1 查看 / 新建 / 切换

点击状态栏或工具栏上的 **VCS widget**（显示当前分支名，如 `main`）：

![分支菜单](https://resources.jetbrains.com/help/img/idea/2026.2/branch_menu.png)

*图：分支列表与操作菜单*

选中基线分支 → **New Branch**：

![新建分支](https://resources.jetbrains.com/help/img/idea/2026.2/new_branch.png)

*图：指定分支名并可选 Checkout*

勾选 **Checkout branch** 可创建后立即切换：

![已切换到新分支](https://resources.jetbrains.com/help/img/idea/2026.2/changed_branch.png)

*图：当前分支已变为新分支*

> 有未提交改动时切换分支，IDEA 会要求先 **Commit / Shelve / Stash**，避免改动丢失。

### 5.2 命名习惯

```text
main / master   ← 稳定、可演示
feature/xxx     ← 新功能
fix/xxx         ← 修 bug
```

流程：本地开发 → Push 远程分支 → GitHub / Gitee 提 PR / MR → 评审后合入主分支。

---

## 六、合并与冲突

### 6.1 合并另一分支

例如在 `main` 上合并 `new_feature`：分支菜单中选中功能分支 → **Merge 'new_feature' into 'main'**：

![Merge into main](https://resources.jetbrains.com/help/img/idea/2026.2/merge_into_main_option.png)

*图：将功能分支合并进当前分支*

### 6.2 解决冲突

两边改了同一处时，弹出 **Conflicts** 对话框：

![Conflicts 对话框](https://resources.jetbrains.com/help/img/idea/2026.2/conflicts_dialog.png)

*图：冲突文件列表与处理选项*

| 选项 | 含义 |
|------|------|
| Accept Yours | 保留当前分支改动 |
| Accept Theirs | 采用被合并分支改动 |
| Merge | 进入三方合并（推荐学习） |

点 **Merge** 进入三方界面：

![Merge Revisions 三方合并](https://resources.jetbrains.com/help/img/idea/2026.2/merge_revisions.png)

*图：左=当前分支，右=进来的分支，中=结果*

- **左**：当前分支（如 `main`）  
- **右**：被合并分支（如 `new_feature`）  
- **中**：结果区，可接受左右改动，也可手改  

解决后点 **Apply**，全部冲突处理完再 Commit / Push。

![冲突已解决](https://resources.jetbrains.com/help/img/idea/2026.2/conflicts_resolved.png)

*图：合并结果确认*

变更文件在项目树中也会用颜色标出：

![变更文件颜色](https://resources.jetbrains.com/help/img/idea/2026.2/vcs_changes.png)

*图：修改 / 新增文件的颜色提示*

---

## 七、查看历史

### 7.1 项目 Git Log

**Git** 工具窗口（`Alt+9`）→ **Log**：按分支过滤、搜作者 / 说明，右键可 Cherry-Pick、Revert 等。

![Git Log](https://resources.jetbrains.com/help/img/idea/2026.2/git_log_tab.png)

*图：Git Log 标签页*

### 7.2 单文件 / 选中代码历史

文件上右键 → **Git → Show History**：

![文件 History](https://resources.jetbrains.com/help/img/idea/2026.2/git_history.png)

*图：单个文件的提交历史*

选中一段代码 → **Git → Show History for Selection**：

![选中代码历史](https://resources.jetbrains.com/help/img/idea/2026.2/history_for_selection.png)

*图：仅查看选中代码片段的历史*

**Annotate / Blame**：Gutter 右键 → **Annotate with Git Blame**，查看每一行最后修改者与提交。

---

## 八、分享到 GitHub（可选）

本地已有仓库时：**Git → GitHub → Share Project on GitHub**：

![Share on GitHub](https://resources.jetbrains.com/help/img/idea/2026.2/add_gh_account.png)

*图：配置账号、仓库名与可见性*

发布成功后，通知中会给出仓库链接：

![已分享到 GitHub](https://resources.jetbrains.com/help/img/idea/2026.2/github_shared.png)

*图：发布成功提示*

也可在网页新建空仓库，再在 IDEA 中 **Git → Manage Remotes…** 添加 `origin` 后 Push。

---

## 九、实用建议与速查表

### 9.1 协作注意点

1. **勿提交密钥**：含 API Key 的本地配置应进 `.gitignore`  
2. **提交粒度**：一个功能 / 一次修复一次提交，便于回滚与 Review  
3. **推送前先 Update**（`Ctrl+T`），减少远程拒绝与冲突  
4. **忽略约定**：`target/`、个人 `.idea` 配置是否提交，团队要统一  
5. **说明写清楚**：Commit Message 写原因，而不是「改了点东西」

### 9.2 IDEA ↔ 命令行对照

| 你想做的事 | IDEA | 命令行大致对应 |
|------------|------|----------------|
| 初始化仓库 | Enable Version Control / 新建勾选 | `git init` |
| 克隆 | Get from VCS / Clone | `git clone` |
| 暂存 | Add / Commit 窗口勾选 | `git add` |
| 提交 | Commit | `git commit` |
| 推送 | Push | `git push` |
| 拉取更新 | Update Project | `git pull` |
| 建分支 | New Branch | `git switch -c` |
| 切分支 | Checkout | `git switch` |
| 合并 | Merge … into … | `git merge` |
| 看日志 | Git → Log | `git log` |
| 看 Diff | Show Diff / Gutter | `git diff` |

---

## 十、小结

日常抓住三条线即可：

```text
Commit 窗口   → 本地改了什么、提交什么
分支 / VCS 条 → 在哪条线开发、合并谁
Git Log       → 历史怎么查、出错怎么回退
```

配图来自 JetBrains 文档资源站（`resources.jetbrains.com`）。若界面与截图略有出入，以本机 IDEA 版本为准。延伸阅读：

- [Getting started with Git](https://www.jetbrains.com/help/idea/working-with-git-tutorial.html)  
- [Commit and push changes](https://www.jetbrains.com/help/idea/commit-and-push-changes.html)  
- [Set up a Git repository](https://www.jetbrains.com/help/idea/set-up-a-git-repository.html)

把命令行会做的那几步在 IDEA 里走通一遍后，冲突合并与历史排查通常会比纯命令行更省心。
