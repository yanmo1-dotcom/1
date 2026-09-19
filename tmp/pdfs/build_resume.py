from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate, Frame, PageTemplate, Paragraph, Spacer,
    KeepTogether, PageBreak
)

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "output" / "pdf" / "Java开发工程师简历.pdf"
OUT.parent.mkdir(parents=True, exist_ok=True)

font_path = ROOT / "kapai" / "kapai-ui" / "src" / "main" / "resources" / "simhei.ttf"
pdfmetrics.registerFont(TTFont("CN", str(font_path)))

PAGE_W, PAGE_H = A4
NAVY = colors.HexColor("#16283B")
BLUE = colors.HexColor("#1F6FB2")
MUTED = colors.HexColor("#526474")
LINE = colors.HexColor("#D8E0E7")
LIGHT = colors.HexColor("#F3F7FA")

styles = getSampleStyleSheet()
base = ParagraphStyle("base", fontName="CN", fontSize=8.3, leading=11.8, textColor=NAVY)
small = ParagraphStyle("small", parent=base, fontSize=7.8, leading=10.8, textColor=MUTED)
title = ParagraphStyle("title", parent=base, fontSize=20, leading=23, textColor=NAVY, alignment=TA_CENTER, spaceAfter=1)
contact = ParagraphStyle("contact", parent=small, alignment=TA_CENTER, fontSize=8, leading=11)
section = ParagraphStyle("section", parent=base, fontSize=11.5, leading=14, textColor=BLUE, spaceBefore=4, spaceAfter=2, borderWidth=0, borderPadding=0)
project = ParagraphStyle("project", parent=base, fontSize=9.3, leading=12.5, textColor=NAVY, spaceBefore=3, spaceAfter=1)
tech = ParagraphStyle("tech", parent=small, fontSize=7.8, leading=10.5, textColor=BLUE, spaceAfter=1)
bullet = ParagraphStyle("bullet", parent=base, leftIndent=10, firstLineIndent=-6, bulletIndent=2, spaceAfter=0.5)

def p(text, style=base):
    return Paragraph(text, style)

def bullets(items):
    return [p("- " + x, bullet) for x in items]

def heading(text):
    return [p(text, section), p("", ParagraphStyle("rule", parent=base, leading=1, borderWidth=0.6, borderColor=LINE, borderPadding=0, spaceAfter=3))]

def project_block(name, stack, items):
    return KeepTogether([p(name, project), p("技术栈：" + stack, tech), *bullets(items)])

def footer(canvas, doc):
    canvas.saveState()
    canvas.setStrokeColor(LINE)
    canvas.setLineWidth(0.5)
    canvas.line(16*mm, 12*mm, PAGE_W-16*mm, 12*mm)
    canvas.setFont("CN", 7.5)
    canvas.setFillColor(MUTED)
    canvas.drawString(16*mm, 7.5*mm, "Java 开发工程师简历｜基于个人项目源码整理")
    canvas.drawRightString(PAGE_W-16*mm, 7.5*mm, f"第 {doc.page} 页")
    canvas.restoreState()

frame = Frame(16*mm, 16*mm, PAGE_W-32*mm, PAGE_H-29*mm, leftPadding=0, rightPadding=0, topPadding=0, bottomPadding=0)
doc = BaseDocTemplate(str(OUT), pagesize=A4, rightMargin=16*mm, leftMargin=16*mm, topMargin=13*mm, bottomMargin=16*mm,
                      title="Java开发工程师简历", author="待补充")
doc.addPageTemplates(PageTemplate(id="resume", frames=frame, onPage=footer))

story = [
    p("姓名（待补充）｜Java 开发工程师", title),
    p("电话：待补充　｜　邮箱：待补充　｜　城市：待补充　｜　GitHub：github.com/yanmo1-dotcom/1", contact),
    Spacer(1, 1.5*mm),
]

story += heading("个人优势")
story += bullets([
    "持续进行 Java 工程实践，完成 14 个个人项目；仓库约 212 个 Java 源文件、2.3 万行 Java 代码。",
    "熟悉 Java 17、面向对象、集合、I/O、多线程、Socket，以及 Maven/Gradle 项目管理。",
    "能使用 Spring Boot、WebSocket/STOMP、JDBC、H2/MySQL 完成实时 Web 应用和数据持久化。",
    "具备 Swing、LibGDX、LWJGL/OpenGL 游戏开发经验；已搭建作品集网站，11 个项目可网页演示。",
])

story += heading("专业技能")
story += bullets([
    "Java：Java 17、OOP、集合、异常、文件 I/O、并发与线程、Socket、Swing",
    "后端：Spring Boot、Spring Web、WebSocket、STOMP、JDBC、REST API、Token/会话鉴权",
    "数据与工具：H2、MySQL、JSON、CSV、Jackson、OkHttp、Maven、Gradle、JUnit 5、Git",
    "游戏：LibGDX、LWJGL、OpenGL、碰撞检测、状态机、对象池、粒子/音频、地图与存档",
])

story += heading("重点项目经历")
story.append(project_block(
    "Web 实时聊天室与在线对战平台｜个人项目",
    "Java 17、Spring Boot 3、WebSocket/STOMP、JDBC、H2/MySQL、JUnit 5",
    [
        "设计用户登录、好友、私聊/群聊、在线状态、房间匹配、战绩排行及后台管理等业务模块。",
        "基于 WebSocket/STOMP 推送实时消息与对战事件；通过会话新鲜度校验处理重复登录和旧会话踢出。",
        "抽象统一 GameEngine 接口，接入五子棋、井字棋、猜数字和石头剪刀布；由服务端校验动作并裁决胜负。",
        "使用 JDBC 支持 H2/MySQL，并实现 JSON 历史数据迁移、战绩持久化及备份。",
        "编写数据库集成测试与引擎行为测试，实测 12 项测试全部通过。",
    ]))

story.append(project_block(
    "Kapai Roguelike 卡牌战斗游戏｜个人项目",
    "Java 17、LibGDX、Maven 多模块、Jackson、JUnit 5",
    [
        "拆分 core、data、ui 三个模块，隔离战斗逻辑、数据加载与界面渲染。",
        "以状态机组织玩家回合、怪物意图、怪物行动和结算，实现牌堆、能量、护盾、状态效果与目标选择。",
        "通过监听器实现遗物触发，使用效果工厂组合伤害、格挡、抽牌和状态附加等行为。",
        "使用 JSON 驱动卡牌配置，并以集成测试覆盖数据加载、出牌伤害及遗物效果。",
    ]))

story.append(project_block(
    "Voxel Terraria 体素沙盒游戏｜个人项目",
    "Java 17、LWJGL 3、OpenGL、Gradle",
    [
        "实现种子地形生成、区块流式加载/卸载、方块放置与破坏、碰撞和射线选取。",
        "使用固定线程池异步生成地形与网格，主线程限制每帧上传数量，降低区块切换卡顿。",
        "实现跨区块访问、太阳光和点光源 BFS 传播，并在卸载时释放 OpenGL 资源。",
        "完成玩家、敌人、掉落物、背包、合成、粒子、音频及世界差异存档。",
    ]))
story.append(project_block(
    "Ice Game 局域网冰球游戏｜个人项目",
    "Java 17、Swing、Socket、Maven",
    [
        "完成单人 AI 与局域网双人模式，包含菜单、计分、胜负、重开和音效反馈。",
        "以 TCP Socket 建立房主/加入者连接，由房主统一计算运动和比分并同步状态。",
        "将网络读写放在守护线程，并通过 Swing 事件线程安全更新界面。",
    ]))

story += heading("其他项目")
story += bullets([
    "奇斯要塞：LibGDX/Gradle 俯视角射击游戏，实现 TileMap、载具/敌人/Boss、武器状态、对象池、音频和计分。",
    "飞机射击：Java Swing 实现敌机/Boss、波次、道具、粒子爆炸、音效和本地最高分。",
    "A 股行情网站：Spring Boot + OkHttp 请求行情接口，定时刷新内存数据并提供多维排行。",
    "Socket 命令行聊天室：使用多线程 Socket 实现服务端、客户端、多房间管理和日志服务。",
    "算法练习：使用 Java 完成石子游戏、最小回文排列、图与字符串等 LeetCode 题目。",
])

story += heading("教育经历")
story += [p("学校名称（待补充）｜专业（待补充）｜学历（待补充）｜起止时间（待补充）", base)]

story += heading("自我评价")
story += [p("对 Java 和游戏/实时交互类项目有持续兴趣，能够从需求拆分、编码、调试到网页展示独立完成作品；愿意补足工程规范、数据库设计和团队协作经验，并通过可运行项目快速学习新技术。", base)]

story += [Spacer(1, 4*mm), p("投递前请补齐姓名、电话、邮箱、城市、教育经历和可访问的作品集网址。", ParagraphStyle("note", parent=small, backColor=LIGHT, borderColor=LINE, borderWidth=0.5, borderPadding=6, leading=13))]

doc.build(story)
print(OUT)
