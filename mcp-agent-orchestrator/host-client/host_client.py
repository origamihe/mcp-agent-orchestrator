#!/usr/bin/env python3
"""
MCP Agent Orchestrator - Host 客户端示例（Python 3.8+）

演示如何从 Desktop / IDE 连接到 MCP Agent Orchestrator，
发送 HostContext 感知消息并接收回复。

安装依赖: pip install websocket-client

用法:
    # Desktop Host 模式
    python host_client.py --host desktop

    # IDE Host 模式
    python host_client.py --host ide

    # 使用 HTTP 模式（无需 WebSocket）
    python host_client.py --host ide --mode http

WebSocket 端点: ws://localhost:8080/ws/host
HTTP 端点:    POST http://localhost:8080/channel/{hostType}/webhook
"""

import argparse
import json
import sys
import time
import uuid
from datetime import datetime

try:
    import websocket
except ImportError:
    print("请安装 websocket-client: pip install websocket-client")
    sys.exit(1)

try:
    import requests
except ImportError:
    requests = None


# ============================================================
# 模拟 Desktop Host 的 HostContext
# ============================================================
def get_desktop_host_context():
    """模拟获取桌面环境信息"""
    return {
        "clipboardContent": "import React from 'react';\n\nconst App = () => {\n  return <div>Hello</div>;\n};",
        "activeWindowTitle": "Visual Studio Code - mcp-agent-orchestrator",
        "activeWindowProcess": "Code.exe",
        "selectedFilePaths": [
            "C:\\project\\src\\Main.java",
            "C:\\project\\src\\Config.java"
        ],
        "screenshotBase64": None,  # 实际桌面客户端会传 base64 截图
        "ocrText": None
    }


# ============================================================
# 模拟 IDE Host 的 HostContext
# ============================================================
def get_ide_host_context():
    """模拟获取 IDE 环境信息"""
    return {
        "currentFilePath": "C:\\project\\src\\main\\java\\com\\mcp\\gateway\\channel\\ChannelOrchestrator.java",
        "currentFileContent": "// 当前打开的文件的完整内容\npublic class ChannelOrchestrator {\n    // ...\n}",
        "cursorLine": 42,
        "selectedCode": "private Mono<Void> handleChat(ChannelMessage msg) {\n    // 需要优化\n}",
        "projectPath": "C:\\project\\mcp-agent-orchestrator\\mcp-agent-orchestrator",
        "projectFiles": [
            "src/main/java/com/mcp/gateway/channel/ChannelOrchestrator.java",
            "src/main/java/com/mcp/common/workspace/Workspace.java",
            "src/main/java/com/mcp/common/channel/HostContext.java",
            "src/main/java/com/mcp/gateway/channel/impl/DesktopHostAdapter.java",
            "src/main/java/com/mcp/gateway/channel/impl/IdeHostAdapter.java"
        ],
        "gitBranch": "main",
        "gitStatus": "M mcp-gateway/src/main/java/.../ChannelOrchestrator.java\n?? mcp-gateway/src/main/java/.../impl/DesktopHostAdapter.java",
        "gitDiff": None,
        "diagnostics": [
            {
                "filePath": "src/main/java/com/mcp/gateway/channel/ChannelOrchestrator.java",
                "severity": "WARNING",
                "line": 42,
                "message": "Method 'handleChat' is too complex (Cyclomatic Complexity = 15)"
            }
        ],
        "terminalCwd": "C:\\project\\mcp-agent-orchestrator",
        "terminalOutput": "BUILD SUCCESS in 2m 34s",
        "ideType": "Rider",
        "language": "java"
    }


# ============================================================
# WebSocket 客户端
# ============================================================
class HostWebSocketClient:
    def __init__(self, host_type, server_url):
        self.host_type = host_type
        self.server_url = server_url
        self.session_id = f"{host_type}-{uuid.uuid4().hex[:8]}"
        self.ws = None

    def connect(self):
        url = self.server_url.replace("http://", "ws://").replace("https://", "wss://")
        ws_url = f"{url}/ws/host"

        print(f"[{self.host_type.upper()}] Connecting to {ws_url} ...")
        self.ws = websocket.WebSocketApp(
            ws_url,
            on_open=self._on_open,
            on_message=self._on_message,
            on_error=self._on_error,
            on_close=self._on_close
        )
        self.ws.run_forever()

    def _on_open(self, ws):
        print(f"[{self.host_type.upper()}] Connected! Session: {self.session_id}")
        print()

    def _on_message(self, ws, raw_message):
        try:
            msg = json.loads(raw_message)
            msg_type = msg.get("type", "")
            channel = msg.get("channelType", "")

            if msg_type == "reply":
                self._handle_reply(msg)
            elif channel == self.host_type:
                self._handle_reply(msg)
            else:
                print(f"[{self.host_type.upper()}] ← {json.dumps(msg, ensure_ascii=False, indent=2)}")
        except json.JSONDecodeError:
            print(f"[{self.host_type.upper()}] ← {raw_message}")

    def _handle_reply(self, msg):
        print(f"\n{'='*60}")
        print(f"[Agent 回复]")
        print(f"{'='*60}")

        host_action = msg.get("hostAction", "SHOW_MESSAGE")
        content = msg.get("content", "")

        print(f"动作: {host_action}")
        print(f"内容: {content}")

        if msg.get("fileEdits"):
            print(f"\n文件修改 ({len(msg['fileEdits'])} 处):")
            for edit in msg["fileEdits"]:
                print(f"  📄 {edit['filePath']}")
                print(f"     行 {edit['startLine']}-{edit['endLine']}")
                if edit.get("diff"):
                    print(f"     diff: {edit['diff']}")

        if msg.get("terminalCommand"):
            print(f"\n终端命令: {msg['terminalCommand']}")

        if msg.get("notificationTitle"):
            print(f"\n通知: {msg['notificationTitle']}")
            if msg.get("notificationBody"):
                print(f"  {msg['notificationBody']}")

        print()

    def _on_error(self, ws, error):
        print(f"[{self.host_type.upper()}] Error: {error}")

    def _on_close(self, ws, close_code, close_msg):
        print(f"[{self.host_type.upper()}] Disconnected (code={close_code})")

    def send_message(self, content, host_context=None):
        if self.ws is None:
            print("[ERROR] Not connected. Call connect() first.")
            return

        message = {
            "hostType": self.host_type,
            "type": "message",
            "content": content,
            "sessionId": self.session_id,
            "userId": f"{self.host_type}-user",
            "hostContext": host_context or {},
            "timestamp": datetime.now().isoformat()
        }

        print(f"[{self.host_type.upper()}] → {content}")
        self.ws.send(json.dumps(message, ensure_ascii=False))


# ============================================================
# HTTP 客户端（备用模式）
# ============================================================
class HostHttpClient:
    def __init__(self, host_type, server_url):
        self.host_type = host_type
        self.server_url = server_url
        self.session_id = f"{host_type}-{uuid.uuid4().hex[:8]}"

    def send_message(self, content, host_context=None):
        url = f"{self.server_url}/channel/{self.host_type}/webhook"
        message = {
            "type": "message",
            "content": content,
            "sessionId": self.session_id,
            "userId": f"{self.host_type}-user",
            "hostContext": host_context or {},
            "timestamp": datetime.now().isoformat()
        }

        print(f"[{self.host_type.upper()}] POST {url}")
        print(f"[{self.host_type.upper()}] → {content}")

        try:
            resp = requests.post(url, json=message, timeout=30)
            print(f"[{self.host_type.upper()}] ← {resp.status_code}: {resp.json()}")
        except Exception as e:
            print(f"[{self.host_type.upper()}] Error: {e}")


# ============================================================
# 交互式命令行
# ============================================================
def run_interactive(client, host_type):
    """交互式命令循环"""
    print(f"\n{'='*60}")
    print(f"MCP Agent Orchestrator - {host_type.upper()} Host Client")
    print(f"{'='*60}")
    print("输入消息发送给 Agent，输入 /quit 退出")
    print()

    if host_type == "ide":
        print("场景示例:")
        print("  - 帮我优化这段代码")
        print("  - 解释这个函数的作用")
        print("  - 修复这个诊断错误")
        print("  - 生成这段代码的单元测试")
        print("  - 查看 git diff 并生成 commit message")
    elif host_type == "desktop":
        print("场景示例:")
        print("  - 总结我的剪贴板内容")
        print("  - 帮我翻译这段文字")
        print("  - 根据当前窗口给我建议")
        print("  - 打开这个文件并分析")
    print()

    while True:
        try:
            user_input = input("> ").strip()
        except (EOFError, KeyboardInterrupt):
            break

        if not user_input:
            continue
        if user_input.lower() in ("/quit", "/exit", "/q"):
            break

        if host_type == "ide":
            host_ctx = get_ide_host_context()
        elif host_type == "desktop":
            host_ctx = get_desktop_host_context()
        else:
            host_ctx = {}

        client.send_message(user_input, host_ctx)

        if isinstance(client, HostHttpClient):
            time.sleep(0.5)


# ============================================================
# 主入口
# ============================================================
def main():
    parser = argparse.ArgumentParser(
        description="MCP Agent Orchestrator - Host 客户端"
    )
    parser.add_argument(
        "--host", choices=["ide", "desktop", "terminal"],
        default="ide", help="Host 类型"
    )
    parser.add_argument(
        "--mode", choices=["ws", "http"], default="ws",
        help="通信模式: ws (WebSocket) 或 http (HTTP 短连接)"
    )
    parser.add_argument(
        "--server", default="http://localhost:8080",
        help="服务器地址"
    )
    parser.add_argument(
        "--message", "-m", default=None,
        help="单次消息（非交互模式）"
    )
    args = parser.parse_args()

    if args.mode == "http" and requests is None:
        print("HTTP 模式需要 requests 库: pip install requests")
        sys.exit(1)

    if args.mode == "ws":
        client = HostWebSocketClient(args.host, args.server)
        if args.message:
            client.connect()
            time.sleep(1)
            host_ctx = get_ide_host_context() if args.host == "ide" else get_desktop_host_context()
            client.send_message(args.message, host_ctx)
            time.sleep(5)
        else:
            run_interactive(client, args.host)
    else:
        client = HostHttpClient(args.host, args.server)
        if args.message:
            host_ctx = get_ide_host_context() if args.host == "ide" else get_desktop_host_context()
            client.send_message(args.message, host_ctx)
        else:
            run_interactive(client, args.host)


if __name__ == "__main__":
    main()