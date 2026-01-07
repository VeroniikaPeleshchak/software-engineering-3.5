import tkinter as tk
from tkinter import ttk
import random

LEVELS = 20            # кількість рядків у кожному стовпчику (висота "циліндру")
CELL_HEIGHT = 14       # висота одного рядка
CELL_WIDTH = 80        # ширина кожного стовпчика
H_GAP = 80             # горизонтальний відступ між стовпчиками
TOP_MARGIN = 20
LEFT_MARGIN = 40

WORK_COLOR = "#0b5cff"
IDLE_COLOR = "#d11b1b"
BG_COLOR = "#000000"
SEP_COLOR = "#e9e9e9"

class ThreadSimApp:
    def __init__(self, root):
        self.root = root
        root.title("Simulation of 3 threads")
        root.configure(bg=BG_COLOR)

        self.canvas_w = LEFT_MARGIN*2 + 3*CELL_WIDTH + 2*H_GAP
        self.canvas_h = TOP_MARGIN*2 + LEVELS*CELL_HEIGHT + 100
        self.canvas = tk.Canvas(root, width=self.canvas_w, height=self.canvas_h, bg=BG_COLOR, highlightthickness=0)
        self.canvas.pack(padx=10, pady=10)

        controls = tk.Frame(root, bg=BG_COLOR)
        controls.pack(fill="x", padx=10)

        tk.Label(controls, text="t1_1", bg=BG_COLOR).grid(row=0, column=0, padx=4)
        self.t1_entry = tk.Entry(controls, width=4)
        self.t1_entry.grid(row=0, column=1, padx=4)
        tk.Label(controls, text="t2_1", bg=BG_COLOR).grid(row=0, column=2, padx=4)
        self.t2_entry = tk.Entry(controls, width=4)
        self.t2_entry.grid(row=0, column=3, padx=4)
        tk.Label(controls, text="t3_1", bg=BG_COLOR).grid(row=0, column=4, padx=4)
        self.t3_entry = tk.Entry(controls, width=4)
        self.t3_entry.grid(row=0, column=5, padx=4)

        tk.Label(controls, text="Max random", bg=BG_COLOR).grid(row=0, column=6, padx=4)
        self.max_rand = tk.Spinbox(controls, from_=1, to=LEVELS, width=4)
        self.max_rand.grid(row=0, column=7, padx=4)
        self.max_rand.delete(0, "end"); self.max_rand.insert(0, "6")

        self.randomize_btn = ttk.Button(controls, text="Randomize initial", command=self.randomize_initial)
        self.randomize_btn.grid(row=0, column=8, padx=8)

        self.start_btn = ttk.Button(controls, text="Start simulation", command=self.start_simulation)
        self.start_btn.grid(row=0, column=9, padx=8)

        self.animate_var = tk.BooleanVar(value=True)
        tk.Checkbutton(controls, text="Animate", variable=self.animate_var, bg=BG_COLOR).grid(row=0, column=10, padx=8)

        self.clear_btn = ttk.Button(controls, text="Clear", command=self.clear_canvas)
        self.clear_btn.grid(row=0, column=11, padx=4)

        self.exit_btn = ttk.Button(controls, text="Exit", command=root.destroy)
        self.exit_btn.grid(row=0, column=12, padx=4)

        self.cols_x = [
            LEFT_MARGIN,
            LEFT_MARGIN + CELL_WIDTH + H_GAP,
            LEFT_MARGIN + 2*(CELL_WIDTH + H_GAP)
        ]

        self.rects = [[None]*LEVELS for _ in range(3)]

        self.clear_canvas()

    def clear_canvas(self):
        self.canvas.delete("all")
        for i, x in enumerate(self.cols_x):
            self.canvas.create_rectangle(x-4, TOP_MARGIN-4,
                                         x + CELL_WIDTH + 4,
                                         TOP_MARGIN + LEVELS*CELL_HEIGHT + 4,
                                         outline="black", width=1, fill="#ffffff", tags="frame")
            for r in range(LEVELS):
                y1 = TOP_MARGIN + r*CELL_HEIGHT
                y2 = y1 + CELL_HEIGHT
                rect = self.canvas.create_rectangle(x, y1+1, x+CELL_WIDTH, y2-1, fill=BG_COLOR, outline=SEP_COLOR)
                self.rects[i][r] = rect

        self.canvas.create_text(self.cols_x[0] + CELL_WIDTH/2, TOP_MARGIN + LEVELS*CELL_HEIGHT + 20, text="Thread 1")
        self.canvas.create_text(self.cols_x[1] + CELL_WIDTH/2, TOP_MARGIN + LEVELS*CELL_HEIGHT + 20, text="Thread 2")
        self.canvas.create_text(self.cols_x[2] + CELL_WIDTH/2, TOP_MARGIN + LEVELS*CELL_HEIGHT + 20, text="Thread 3")

    def randomize_initial(self):
        m = int(self.max_rand.get())
        self.t1_entry.delete(0, "end"); self.t1_entry.insert(0, str(random.randint(1, m)))
        self.t2_entry.delete(0, "end"); self.t2_entry.insert(0, str(random.randint(1, m)))
        self.t3_entry.delete(0, "end"); self.t3_entry.insert(0, str(random.randint(1, m)))

    def start_simulation(self):
        try:
            t1 = int(self.t1_entry.get()) if self.t1_entry.get() else None
            t2 = int(self.t2_entry.get()) if self.t2_entry.get() else None
            t3 = int(self.t3_entry.get()) if self.t3_entry.get() else None
        except ValueError:
            tk.messagebox.showerror("Input error", "Initial times must be integers or empty.")
            return

        m = int(self.max_rand.get())
        if t1 is None: t1 = random.randint(1, m)
        if t2 is None: t2 = random.randint(1, m)
        if t3 is None: t3 = random.randint(1, m)

        states = self.run_discrete_simulation(initials=[t1, t2, t3], max_random=m)

        if self.animate_var.get():
            self.animate_draw(states)
        else:
            self.draw_states(states)

    def run_discrete_simulation(self, initials, max_random=6):
        num_threads = 3
        remaining = [0]*num_threads
        queues = [[] for _ in range(num_threads)]
        states = [["IDLE"]*LEVELS for _ in range(num_threads)]

        for i in range(num_threads):
            remaining[i] = max(0, int(initials[i]))

        for time in range(LEVELS):
            finished_now = []
            for i in range(num_threads):
                if remaining[i] > 0:
                    states[i][time] = "WORK"
                    remaining[i] -= 1
                    if remaining[i] == 0:
                        finished_now.append(i)
                else:
                    states[i][time] = "IDLE"
            for i in finished_now:
                nxt = (i+1) % num_threads
                new_duration = random.randint(1, max_random)
                queues[nxt].append(new_duration)
            for i in range(num_threads):
                if remaining[i] == 0 and queues[i]:
                    remaining[i] = queues[i].pop(0)
        return states

    def draw_states(self, states):
        for i in range(3):
            x = self.cols_x[i]
            for r in range(LEVELS):
                rect_id = self.rects[i][r]
                state = states[i][r]
                color = WORK_COLOR if state == "WORK" else IDLE_COLOR
                self.canvas.itemconfigure(rect_id, fill=color, outline=SEP_COLOR)

    def animate_draw(self, states, step=0):
        if step >= LEVELS:
            return
        for i in range(3):
            rect_id = self.rects[i][step]
            color = WORK_COLOR if states[i][step] == "WORK" else IDLE_COLOR
            self.canvas.itemconfigure(rect_id, fill=color, outline=SEP_COLOR)
        self.root.after(150, lambda: self.animate_draw(states, step+1))


if __name__ == "__main__":
    root = tk.Tk()
    app = ThreadSimApp(root)
    root.mainloop()

