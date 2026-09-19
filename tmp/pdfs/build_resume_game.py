# -*- coding: utf-8 -*-
"""游戏开发工程师简历生成脚本（面向游戏开发岗位，内容基于 JavaProject 源码整理）"""
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

ROOT = Path(__file__).resolve().parents[2]          # JavaProject
OUT_DIR = ROOT / "output" / "pdf"
OUT = OUT_DIR / "游戏开发工程师简历.pdf"
OUT_DIR.mkdir(parents=True, exist_ok=True)

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
subtitle = ParagraphStyle("subtitle", parent=small, alignment=TA_CENTER, fontSize=8.5, leading=11, textColor=BLUE, spaceAfter=1)
contact = ParagraphStyle("contact", parent=small, alignment=TA_CENTER, fontSize=8, leading=11)
section = ParagraphStyle("section", parent=base, fontSize=11.5, leading=14, textColor=BLUE, spaceBefore=5, spaceAfter=2)
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
    canvas.drawString(16*mm, 7.5*mm, "游戏开发工程师简历｜基于个人项目源码整理")
    canvas.drawRightString(PAGE_W-16*mm, 7.5*mm, f"第 {doc.page} 页")
    canvas.restoreState()

frame = Frame(16*mm, 16*mm, PAGE_W-32*mm, PAGE_H-29*mm, leftPadding=0, rightPadding=0, topPadding=0, bottomPadding=0)
doc = BaseDocTemplate(str(OUT), pagesize=A4, rightMargin=16*mm, leftMargin=16*mm, topMargin=13*mm, bottomMargin=16*mm,
                      title="游戏开发工程师简历", author="常天昊")
doc.addPageTemplates(PageTemplate(id="resume", frames=frame, onPage=footer))

story = [
    p("常天昊｜游戏开发工程师", title),
    p("求职方向：Java 游戏客户端 / 游戏逻辑开发（可胜任游戏服务端）", subtitle),
    p("电话：18516898857　｜　邮箱：cdl24523452@163.com　｜　城市：北京　｜　GitHub：github.com/yanmo1-dotcom/1", contact),
    Spacer(1, 1.5*mm),
]

story += heading("个人优势")
story += bullets([
    "独立开发 8 款可运行的桌面/网页游戏，覆盖沙盒、射击、卡牌、对战等品类，累计 200+ Java 源文件、近 3 万行代码，多数项目可在线试玩。",
    "熟悉游戏开发完整链路：游戏循环、渲染、物理与碰撞、状态机、敌人/Boss AI、对象池、粒子与音效、程序化地图、存档，及多人联机。",
    "掌握 LibGDX、LWJGL/OpenGL 图形接口与自研 Swing 渲染管线，具备渲染性能优化实践（滚动缓存、异步地形网格、BFS 光照）。",
    "独立实现局域网多人联机与 Web 实时对战（UDP 房间发现、TCP 主机权威同步、WebSocket/STOMP），可同时承担游戏客户端与服务端工作。",
    "关注玩法手感与性能细节（重力、惯性、难度曲线、FPS 稳定性），能从创意、编码、测试到网页演示独立交付完整可玩作品。",
])

story += heading("专业技能")
story += bullets([
    "语言基础：Java 17/21/26、OOP、集合、I/O、并发与多线程、Socket、算法与数据结构",
    "游戏引擎/图形：LibGDX、LWJGL 3、OpenGL、Swing 自绘渲染、GWT 网页版；滚动缓存渲染、异步网格生成、BFS 光照传播",
    "游戏系统：游戏循环、碰撞检测、物理积分、状态机、敌人/Boss 行为 AI、程序化地图与生物群系、对象池、粒子系统、音频、摄像机、UI/HUD、存档",
    "网络联机：TCP/UDP、主机权威模型、状态同步与插值、局域网房间发现、WebSocket/STOMP 实时对战",
    "后端与工具：Spring Boot、JDBC、H2/MySQL、JSON/CSV、Maven、Gradle 多模块、JUnit 5、Git",
    "游戏数学：程序化生成、噪声地形、空间检索、物理运动学、博弈与 DP 算法（LeetCode 实战）",
])

story += heading("重点项目经历")
story.append(project_block(
    "泰拉瑞亚 Lite｜横版沙盒游戏 + 局域网联机（个人项目）",
    "Java（JDK 26）、Swing、TCP/UDP、多线程，无第三方依赖",
    [
        "实现“挖掘→收集→建造→合成→战斗”完整玩法闭环；程序化生成森林/沙漠/雪原/丛林四大生物群系与平原、丘陵、山地地形。",
        "实现史莱姆/僵尸行为 AI 与克苏鲁之眼 Boss（飞行追踪、周期冲刺、半血狂暴、顶部血条），完成近战挥砍弧光与远程弓重力弹道。",
        "设计主机权威联机模型：UDP 广播房间发现、TCP 100ms 状态同步与远端平滑插值、增量方块事件、全服聊天，支持 5 人同场。",
        "实现方块层滚动缓存渲染与可见范围光照，实测移动/持续战斗下 FPS 稳定 170~210，显著降低卡顿。",
        "编写 130+ 项断言的无 GUI 自测（逻辑、联机协议、地形生成、Boss、背包、局域网发现）并全部通过。",
    ]))

story.append(project_block(
    "体素泰拉瑞亚｜LWJGL/OpenGL 3D 体素生存（个人项目）",
    "Java 17、LWJGL 3、OpenGL、Gradle",
    [
        "实现种子地形生成、区块流式加载/卸载、方块放置与破坏、碰撞与射线拾取；固定线程池异步生成地形与网格、主线程限帧上传。",
        "实现六大 Boss 多阶段 AI、NPC 入住判定、血月/雨天/哥布林入侵等世界事件，以及木石铁金→首领装→暗影/寒霜/蜂甲的完整装备进阶链。",
        "局域网房主权威：客机挖掘/放置经请求-校验-写入，怪物 AI 与伤害由房主裁定，战利品归属同步给攻击者。",
        "实现太阳光与点光源 BFS 传播、跨区块访问，并在区块卸载时正确释放 OpenGL 资源。",
    ]))

story.append(project_block(
    "Kapai Roguelike 卡牌战斗游戏｜libGDX 多模块（个人项目）",
    "Java 17、LibGDX、Maven 多模块、Jackson、JUnit 5",
    [
        "拆分 core/data/ui 三模块，隔离战斗逻辑、数据加载与界面渲染，便于独立测试与替换。",
        "以状态机组织玩家回合、怪物意图、行动与结算，实现牌堆、能量、护盾、状态效果与目标选择。",
        "使用效果工厂组合伤害/格挡/抽牌/状态附加，通过监听器驱动遗物触发；JSON 驱动卡牌配置，集成测试覆盖数据加载、出牌伤害与遗物效果。",
    ]))

story.append(project_block(
    "奇斯要塞｜libGDX 俯视角策略射击（个人项目）",
    "Java、LibGDX、Gradle、GWT",
    [
        "实现步兵/吉普/坦克多载具、敌人与 Boss、武器状态机、TileMap 关卡与计分系统。",
        "使用对象池管理弹体与特效，配以粒子与音效反馈；第二版通过 GWT 编译为网页可玩版本，浏览器即点即玩。",
    ]))

story.append(PageBreak())

story.append(project_block(
    "飞机射击 Racing Game｜Swing 弹幕射击（个人项目）",
    "Java、Swing、音频",
    [
        "实现多类型敌机、Boss、波次管理、道具掉落、粒子爆炸与音效反馈，含本地最高分存档。",
    ]))

story.append(project_block(
    "冰球游戏｜Swing 局域网对战（个人项目）",
    "Java、Swing、Socket、Maven",
    [
        "完成单人 AI 与局域网双人模式，含菜单、计分、胜负、重开与音效；房主统一计算球运动与比分保证画面一致。",
        "网络读写置于守护线程，并通过 Swing 事件线程安全更新界面。",
    ]))

story += heading("其他项目")
story += bullets([
    "Web 实时聊天与在线对战平台：Spring Boot + WebSocket/STOMP，接入五子棋/井字棋/猜数字/石头剪刀布，服务端校验动作并裁决胜负。",
    "A 股实时行情网站：Spring Boot + OkHttp 拉取行情接口，定时刷新内存数据并提供多维排行。",
    "命令行多房间聊天室：多线程 Socket 实现服务端、客户端、多房间管理与日志服务。",
    "LeetCode 算法练习：Java 完成石子游戏、最小回文排列、图与字符串等题目（博弈、DP、贪心）。",
])

story += heading("教育经历")
story += [p("河北司法警官职业学院｜罪犯心理测量与矫正技术｜大专｜2023.09 - 2026.06", base)]

story += heading("自我评价")
story += [p("对游戏开发有持续热情，能从想法、玩法设计、编码、测试到网页演示独立完成一款可玩的小游戏；注重手感与性能细节，善于通过可运行项目快速学习新引擎与新技术。希望进入游戏行业，参与从原型到上线的完整流程，并快速补足工程规范与团队协作经验。", base)]

story += [Spacer(1, 4*mm), p("如需突出服务端方向或某类游戏品类，可据此模板微调项目排序与措辞；作品集在线演示页与 GitHub 仓库可一并附在联系方式中。", ParagraphStyle("note", parent=small, backColor=LIGHT, borderColor=LINE, borderWidth=0.5, borderPadding=6, leading=13))]

doc.build(story)
print(OUT)
