package com.kapai.core.enums;

/**
 * 战斗目标选取范围。
 * 自身 SELF、单体 SINGLE（需调用方传入 target）、全体 ALL_ENEMIES、无目标 NONE。
 */
public enum CardTarget {
    SELF,
    SINGLE,
    ALL_ENEMIES,
    NONE
}
