import socket
import threading
import sys

HOST = "0.0.0.0"
PORT = 5055

def handle_client(conn, addr):
    print(f"[INFO] Підключено клієнта: {addr}")

    with conn:
        while True:
            data = conn.recv(1024)

            if not data:
                print(f"[INFO] Клієнт {addr} відключився")
                break

            msg = data.decode("utf-8").strip()
            print(f"[RECV] {addr}: {msg}")


            parts = msg.split()

            if len(parts) == 3:
                cmd = parts[0].lower()
                try:
                    a = float(parts[1])
                    b = float(parts[2])
                except ValueError:
                    conn.sendall("Помилка: аргументи повинні бути числами.\n".encode("utf-8"))
                    continue

                if cmd == "add":
                    conn.sendall(f"Результат: {a + b}\n".encode("utf-8"))
                    continue

                if cmd == "sub":
                    conn.sendall(f"Результат: {a - b}\n".encode("utf-8"))
                    continue

                if cmd == "mul":
                    conn.sendall(f"Результат: {a * b}\n".encode("utf-8"))
                    continue

                if cmd == "div":
                    if b == 0:
                        conn.sendall("Помилка: ділення на нуль.\n".encode("utf-8"))
                    else:
                        conn.sendall(f"Результат: {a / b}\n".encode("utf-8"))
                    continue

                if cmd == "pow":
                    conn.sendall(f"Результат: {a ** b}\n".encode("utf-8"))
                    continue


            if len(parts) == 2:
                cmd = parts[0].lower()
                try:
                    x = float(parts[1])
                except ValueError:
                    conn.sendall("Помилка: аргумент повинен бути числом.\n".encode("utf-8"))
                    continue

                if cmd == "sqrt":
                    import math
                    if x < 0:
                        conn.sendall("Помилка: не можна брати корінь з від'ємного числа.\n".encode("utf-8"))
                    else:
                        conn.sendall(f"Результат: {math.sqrt(x)}\n".encode("utf-8"))
                    continue


            if msg.lower() == "quit":
                conn.sendall("З'єднання завершено.\n".encode("utf-8"))
                break

            response = f"Сервер отримав: {msg}\n"
            conn.sendall(response.encode("utf-8"))

    print(f"[INFO] Сеанс завершено: {addr}")


def run_sequential(listener):
    print("[MODE] Послідовний режим")

    while True:
        conn, addr = listener.accept()
        handle_client(conn, addr)


def run_parallel(listener):
    print("[MODE] Паралельний режим")

    while True:
        conn, addr = listener.accept()
        thread = threading.Thread(target=handle_client, args=(conn, addr), daemon=True)
        thread.start()
        print(f"[THREAD] Створено потік для клієнта {addr}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(1)

    mode = sys.argv[1].lower()

    listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    listener.bind((HOST, PORT))
    listener.listen(5)

    print(f"[INFO] Сервер запущено на {HOST}:{PORT}")

    if mode == "sequential":
        run_sequential(listener)
    elif mode == "parallel":
        run_parallel(listener)
    else:
        print("Використовуйте: sequential / parallel")
