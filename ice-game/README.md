# Ice Game（Java 冰球游戏）

一个使用 Java Swing 编写的桌面冰球游戏，包含：

- 单人模式：玩家（左侧）对战电脑 AI（右侧）
- 双人联机：一名玩家创建房间，另一名玩家通过房主 IP 加入
- 先得 7 分者获胜；按 `R` 可重新开始，按 `Esc` 返回菜单

## 运行环境

- JDK 17 或更高版本
- Maven 3.8 或更高版本

## 启动

在 `ice-game` 目录执行：

```powershell
mvn compile exec:java
```

也可以不使用 Maven：

```powershell
javac -encoding UTF-8 -d out src/main/java/com/icegame/*.java
java -cp out com.icegame.Main
```

## 操作方式

- 移动鼠标：控制自己的球拍上下移动
- `W` / `S`：也可以控制球拍上下移动
- 联机房主：点击“创建房间”，把界面显示的本机 IPv4 地址告诉另一位玩家
- 加入者：点击“加入房间”，输入房主的 IPv4 地址
- 默认端口：`5050`。若跨设备无法连接，请允许 Java 通过防火墙，并确认两台电脑在同一局域网中

联机时由房主计算球的运动和比分，因此双方画面会保持一致。
