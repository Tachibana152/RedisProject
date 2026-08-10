# -*- coding: utf-8 -*-
"""
简易 Redis RESP 客户端（基于 socket，无第三方依赖）
用法示例：
  python redis_ops.py "GET seckill:stock:15"
  python redis_ops.py "SET seckill:stock:15 100"
  python redis_ops.py "DEL seckill:order:15"
支持引号包裹带空格的参数：python redis_ops.py "SET key 'hello world'"
"""
import socket
import sys

HOST = "192.168.184.128"
PORT = 6379
PASSWORD = "07210721"


def encode_command(args):
    out = b"*%d\r\n" % len(args)
    for a in args:
        b = a.encode("utf-8")
        out += b"$%d\r\n%s\r\n" % (len(b), b)
    return out


def read_reply(f):
    line = f.readline()
    if not line:
        raise EOFError("连接已关闭")
    t, _, rest = line[:1], b"", line[1:-2]
    if t == b"+":
        return rest.decode("utf-8")
    if t == b"-":
        return "ERROR: " + rest.decode("utf-8")
    if t == b":":
        return int(rest)
    if t == b"$":
        n = int(rest)
        if n == -1:
            return None
        data = f.read(n)
        f.read(2)  # \r\n
        return data.decode("utf-8")
    if t == b"*":
        n = int(rest)
        if n == -1:
            return None
        return [read_reply(f) for _ in range(n)]
    raise ValueError("未知响应类型: %r" % line)


def main():
    args = sys.argv[1].split()
    # 解析带引号的参数
    import shlex
    args = shlex.split(sys.argv[1])

    sock = socket.create_connection((HOST, PORT), timeout=10)
    f = sock.makefile("rb")
    sock.sendall(encode_command(["AUTH", PASSWORD]))
    auth = read_reply(f)
    if auth != "OK":
        print("AUTH 失败:", auth)
        return 1
    sock.sendall(encode_command(args))
    reply = read_reply(f)
    if isinstance(reply, list):
        print("(%d items)" % len(reply))
        for item in reply:
            print(" ", item)
    else:
        print(reply)
    sock.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
