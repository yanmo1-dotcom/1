# -*- coding: utf-8 -*-
from docx import Document
d = Document(r"D:\laogedekecheng\java\JavaProject\output\pdf\游戏开发工程师简历.docx")
paras = [p.text for p in d.paragraphs if p.text.strip()]
print("段落数:", len(paras))
print("首段:", paras[0])
checks = ["个人优势", "专业技能", "重点项目经历", "泰拉瑞亚 Lite", "体素泰拉瑞亚", "Kapai", "奇斯要塞", "飞机射击", "冰球游戏", "其他项目", "教育经历", "自我评价"]
for c in checks:
    print("包含 %s:" % c, any(c in x for x in paras))
print("末段:", paras[-1])
