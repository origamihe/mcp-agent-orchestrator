package com.mcp.tools.tool;

import java.awt.*;

/**
 * 文档主题——把字号、颜色、间距等渲染参数从代码中抽离。
 */
public sealed interface DocumentTheme {

    String name();

    sealed interface DocxThemeConfig extends DocumentTheme {
        String fontFamily();
        int titleFontSize();
        int h1FontSize();
        int h2FontSize();
        int h3FontSize();
        int bodyFontSize();
        int lineSpacing();          // 行距，单位 twips (1/20 pt)
        int spaceBefore();          // 段前
        int spaceAfter();           // 段后
        int firstLineIndent();      // 首行缩进
        Color titleColor();
        Color headingColor();
        Color bodyColor();
    }

    sealed interface PptThemeConfig extends DocumentTheme {
        String fontFamily();
        int coverTitleSize();
        int slideTitleSize();
        int bodyFontSize();
        int smallFontSize();
        Color titleColor();
        Color bodyColor();
        Color accentColor();
        Color backgroundColor();
        java.awt.Dimension slideSize();
    }

    // ===== 内置主题 =====

    record Academic() implements DocxThemeConfig, PptThemeConfig {
        @Override public String name() { return "academic"; }
        @Override public String fontFamily() { return "SimSun"; }
        @Override public int titleFontSize() { return 22; }
        @Override public int h1FontSize() { return 16; }
        @Override public int h2FontSize() { return 14; }
        @Override public int h3FontSize() { return 13; }
        @Override public int bodyFontSize() { return 12; }
        @Override public int lineSpacing() { return 360; }
        @Override public int spaceBefore() { return 120; }
        @Override public int spaceAfter() { return 60; }
        @Override public int firstLineIndent() { return 480; }
        @Override public Color titleColor() { return new Color(0x00, 0x00, 0x00); }
        @Override public Color headingColor() { return new Color(0x00, 0x00, 0x00); }
        @Override public Color bodyColor() { return new Color(0x33, 0x33, 0x33); }
        @Override public int coverTitleSize() { return 32; }
        @Override public int slideTitleSize() { return 24; }
        @Override public int smallFontSize() { return 14; }
        @Override public Color accentColor() { return new Color(0x1A, 0x56, 0xDB); }
        @Override public Color backgroundColor() { return Color.WHITE; }
        @Override public java.awt.Dimension slideSize() { return new java.awt.Dimension(960, 540); }
    }

    record Business() implements DocxThemeConfig, PptThemeConfig {
        @Override public String name() { return "business"; }
        @Override public String fontFamily() { return "Microsoft YaHei"; }
        @Override public int titleFontSize() { return 24; }
        @Override public int h1FontSize() { return 18; }
        @Override public int h2FontSize() { return 16; }
        @Override public int h3FontSize() { return 14; }
        @Override public int bodyFontSize() { return 12; }
        @Override public int lineSpacing() { return 320; }
        @Override public int spaceBefore() { return 80; }
        @Override public int spaceAfter() { return 40; }
        @Override public int firstLineIndent() { return 400; }
        @Override public Color titleColor() { return new Color(0x1A, 0x1A, 0x2E); }
        @Override public Color headingColor() { return new Color(0x1A, 0x1A, 0x2E); }
        @Override public Color bodyColor() { return new Color(0x33, 0x33, 0x33); }
        @Override public int coverTitleSize() { return 36; }
        @Override public int slideTitleSize() { return 28; }
        @Override public int smallFontSize() { return 16; }
        @Override public Color accentColor() { return new Color(0x66, 0x7E, 0xEA); }
        @Override public Color backgroundColor() { return Color.WHITE; }
        @Override public java.awt.Dimension slideSize() { return new java.awt.Dimension(960, 540); }
    }

    record Minimal() implements DocxThemeConfig, PptThemeConfig {
        @Override public String name() { return "minimal"; }
        @Override public String fontFamily() { return "Microsoft YaHei"; }
        @Override public int titleFontSize() { return 20; }
        @Override public int h1FontSize() { return 16; }
        @Override public int h2FontSize() { return 14; }
        @Override public int h3FontSize() { return 13; }
        @Override public int bodyFontSize() { return 11; }
        @Override public int lineSpacing() { return 280; }
        @Override public int spaceBefore() { return 60; }
        @Override public int spaceAfter() { return 20; }
        @Override public int firstLineIndent() { return 0; }
        @Override public Color titleColor() { return new Color(0x33, 0x33, 0x33); }
        @Override public Color headingColor() { return new Color(0x33, 0x33, 0x33); }
        @Override public Color bodyColor() { return new Color(0x55, 0x55, 0x55); }
        @Override public int coverTitleSize() { return 30; }
        @Override public int slideTitleSize() { return 24; }
        @Override public int smallFontSize() { return 14; }
        @Override public Color accentColor() { return new Color(0x88, 0x88, 0x88); }
        @Override public Color backgroundColor() { return Color.WHITE; }
        @Override public java.awt.Dimension slideSize() { return new java.awt.Dimension(960, 540); }
    }

    record Report() implements DocxThemeConfig, PptThemeConfig {
        @Override public String name() { return "report"; }
        @Override public String fontFamily() { return "Microsoft YaHei"; }
        @Override public int titleFontSize() { return 26; }
        @Override public int h1FontSize() { return 20; }
        @Override public int h2FontSize() { return 16; }
        @Override public int h3FontSize() { return 14; }
        @Override public int bodyFontSize() { return 12; }
        @Override public int lineSpacing() { return 360; }
        @Override public int spaceBefore() { return 120; }
        @Override public int spaceAfter() { return 60; }
        @Override public int firstLineIndent() { return 400; }
        @Override public Color titleColor() { return new Color(0x0D, 0x2B, 0x4E); }
        @Override public Color headingColor() { return new Color(0x0D, 0x2B, 0x4E); }
        @Override public Color bodyColor() { return new Color(0x33, 0x33, 0x33); }
        @Override public int coverTitleSize() { return 34; }
        @Override public int slideTitleSize() { return 26; }
        @Override public int smallFontSize() { return 15; }
        @Override public Color accentColor() { return new Color(0x2E, 0x86, 0xC1); }
        @Override public Color backgroundColor() { return Color.WHITE; }
        @Override public java.awt.Dimension slideSize() { return new java.awt.Dimension(960, 540); }
    }

    static DocumentTheme of(String name) {
        return switch (name != null ? name.toLowerCase() : "business") {
            case "academic" -> new Academic();
            case "minimal" -> new Minimal();
            case "report" -> new Report();
            default -> new Business();
        };
    }
}