"""
Rate Limiter Load Test
Simulates concurrent multi-user traffic to stress-test the Token Bucket rate limiter.
Usage: python load_test.py [--requests N] [--users N] [--host URL]
"""

import urllib.request
import urllib.error
import threading
import time
import json
import argparse
import random

# --------------- Configuration ---------------
parser = argparse.ArgumentParser(description="Rate Limiter Load Test")
parser.add_argument("--requests", type=int, default=1000, help="Total requests to make")
parser.add_argument("--users", type=int, default=10, help="Number of concurrent users")
parser.add_argument("--host", type=str, default="http://127.0.0.1:8080", help="Base URL of the rate limiter")
args = parser.parse_args()

TOTAL_REQUESTS = args.requests
NUM_USERS = args.users
BASE_URL = args.host

URL_ALLOW = f"{BASE_URL}/allow?userId="
URL_METRICS = f"{BASE_URL}/metrics"

# --------------- Shared State ---------------
results_per_user = {f"user_{i}": {"200": 0, "429": 0, "other": 0} for i in range(1, NUM_USERS + 1)}
global_results = {"200": 0, "429": 0, "other": 0}
lock = threading.Lock()

def make_request():
    user = f"user_{random.randint(1, NUM_USERS)}"
    url = f"{URL_ALLOW}{user}"
    
    try:
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req) as response:
            with lock:
                results_per_user[user]["200"] += 1
                global_results["200"] += 1
    except urllib.error.HTTPError as e:
        with lock:
            if e.code == 429:
                results_per_user[user]["429"] += 1
                global_results["429"] += 1
            else:
                results_per_user[user]["other"] += 1
                global_results["other"] += 1
    except Exception:
        with lock:
            results_per_user[user]["other"] += 1
            global_results["other"] += 1

# --------------- Bar helper ---------------
def bar(value, max_val, width=20):
    if max_val == 0:
        return " " * width
    filled = int(value / max_val * width)
    return "█" * filled + "░" * (width - filled)

# --------------- Run ---------------
print(f"\n{'='*60}")
print(f"  RATE LIMITER LOAD TEST")
print(f"{'='*60}")
print(f"  Target:    {BASE_URL}")
print(f"  Requests:  {TOTAL_REQUESTS}")
print(f"  Users:     {NUM_USERS}")
print(f"{'='*60}\n")

start_time = time.perf_counter()

threads = []
for _ in range(TOTAL_REQUESTS):
    t = threading.Thread(target=make_request)
    threads.append(t)
    t.start()

for t in threads:
    t.join()

total_seconds = time.perf_counter() - start_time

# --------------- Results ---------------
total = global_results["200"] + global_results["429"] + global_results["other"]

print(f"{'='*60}")
print(f"  RESULTS")
print(f"{'='*60}")
print(f"  Duration:    {total_seconds:.2f}s")
print(f"  Throughput:  {total / total_seconds:.0f} req/s")
print()
print(f"  Accepted (200):  {global_results['200']:>5}  {bar(global_results['200'], total)}")
print(f"  Rejected (429):  {global_results['429']:>5}  {bar(global_results['429'], total)}")
if global_results["other"] > 0:
    print(f"  Errors:          {global_results['other']:>5}  {bar(global_results['other'], total)}")

# Per-user breakdown
print(f"\n{'='*60}")
print(f"  PER-USER BREAKDOWN")
print(f"{'='*60}")
print(f"  {'User':<10} {'Accepted':>8} {'Rejected':>8} {'Total':>7}")
print(f"  {'─'*10} {'─'*8} {'─'*8} {'─'*7}")

for i in range(1, NUM_USERS + 1):
    u = f"user_{i}"
    res = results_per_user[u]
    total_u = res["200"] + res["429"] + res["other"]
    print(f"  {u:<10} {res['200']:>8} {res['429']:>8} {total_u:>7}")

# Server-side metrics (includes real latency measured by Java)
print(f"\n{'='*60}")
print(f"  SERVER METRICS  (measured server-side by Java)")
print(f"{'='*60}")
try:
    with urllib.request.urlopen(URL_METRICS) as response:
        metrics = json.loads(response.read().decode())
        print(f"  Allowed (total):   {metrics.get('allowedRequests')}")
        print(f"  Rejected (total):  {metrics.get('rejectedRequests')}")
        print(f"  Processed:         {metrics.get('totalProcessed')}")
        avg = metrics.get('avgLatencyMs', 0)
        mx = metrics.get('maxLatencyMs', 0)
        print(f"  Avg latency:       {avg:.3f} ms")
        print(f"  Max latency:       {mx:.3f} ms")
except Exception as e:
    print(f"  Error fetching metrics: {e}")

print(f"{'='*60}\n")
