package com.mcp.tools.model;

/**
 * PPT 页面类型——决定版式布局
 */
public enum SlideType {
    COVER,              // 封面
    AGENDA,             // 目录页
    SECTION_HEADER,     // 章节分隔页
    BULLET,             // 要点页（默认）
    TWO_COLUMN,         // 双栏对比
    TABLE,              // 表格页
    CHART,              // 图表页
    IMAGE,              // 图片页
    CONCLUSION,         // 结论/总结页
    BLANK               // 空白页
}