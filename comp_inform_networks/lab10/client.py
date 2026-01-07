import socket
import sys

if len(sys.argv) != 3:
    sys.exit(1)

HOST = sys.argv[1]
PORT = int(sys.argv[2])

with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
    print(f"[INFO] Підключення до {HOST}:{PORT} ...")
    s.connect((HOST, PORT))
    print("[INFO] Підключено.")

    while True:
        msg = input("Введіть текст (або 'quit' щоб вийти): ")

        s.sendall((msg + "\n").encode("utf-8"))

        if msg.lower() == "quit":
            data = s.recv(1024).decode("utf-8")
            print(data)
            break

        data = s.recv(1024).decode("utf-8")
        print("Відповідь сервера:", data.strip())
