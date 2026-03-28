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
parser.add_argument("--requests", type=int, default=1000, help="Total requests in batch mode")
parser.add_argument("--users", type=int, default=10, help="Number of concurrent users")
parser.add_argument("--host", type=str, default="http://127.0.0.1:8080", help="Base URL")
parser.add_argument("--continuous", action="store_true", help="Run forever with random delays")
parser.add_argument("--delay", type=float, default=0.2, help="Delay between waves in continuous mode")
args = parser.parse_args()

TOTAL_REQUESTS = args.requests
NUM_USERS = args.users
BASE_URL = args.host
CONTINUOUS = args.continuous
DELAY = args.delay

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
                if user in results_per_user:
                    results_per_user[user]["200"] += 1
                global_results["200"] += 1
    except urllib.error.HTTPError as e:
        with lock:
            if e.code == 429:
                if user in results_per_user:
                    results_per_user[user]["429"] += 1
                global_results["429"] += 1
            else:
                if user in results_per_user:
                    results_per_user[user]["other"] += 1
                global_results["other"] += 1
    except Exception:
        with lock:
            if user in results_per_user:
                results_per_user[user]["other"] += 1
            global_results["other"] += 1

# --------------- Bar helper ---------------
def bar(value, max_val, width=20):
    if max_val == 0:
        return " " * width
    filled = int(value / (max_val if max_val > 0 else 1) * width)
    return "█" * filled + "░" * (width - filled)

# --------------- Run ---------------
print(f"\n{'='*60}")
print(f"  RATE LIMITER LOAD TEST - {'CONTINUOUS' if CONTINUOUS else 'BATCH'} MODE")
print(f"{'='*60}")
print(f"  Target:    {BASE_URL}")
if CONTINUOUS:
    print(f"  Mode:      Continuous (Delay: {DELAY}s)")
else:
    print(f"  Requests:  {TOTAL_REQUESTS}")
print(f"  User Pool: {NUM_USERS}")
print(f"{'='*60}\n")

try:
    if CONTINUOUS:
        print("Running in CHAOS MODE (Random bursts)... Press Ctrl+C to stop.\n")
        while True:
            # Random wait between bursts (chaos)
            time.sleep(random.uniform(0.1, 1.5))
            
            # Send a burst of concurrent requests
            burst_size = random.randint(5, 20)
            for _ in range(burst_size):
                threading.Thread(target=make_request).start()
            
            print(f"  Chaos Progress: {global_results['200']} OK, {global_results['429']} Rejected", end="\r")
    else:
        start_time = time.perf_counter()
        threads = []
        for _ in range(TOTAL_REQUESTS):
            t = threading.Thread(target=make_request)
            threads.append(t)
            t.start()
        for t in threads:
            t.join()
        total_seconds = time.perf_counter() - start_time
        
        # --------------- Print Results (Batch Only) ---------------
        total = global_results["200"] + global_results["429"] + global_results["other"]
        print(f"{'='*60}")
        print(f"  RESULTS")
        print(f"{'='*60}")
        print(f"  Duration:    {total_seconds:.2f}s")
        print(f"  Throughput:  {total / (total_seconds if total_seconds > 0 else 1):.0f} req/s")
        print()
        print(f"  Accepted (200):  {global_results['200']:>5}  {bar(global_results['200'], total)}")
        print(f"  Rejected (429):  {global_results['429']:>5}  {bar(global_results['429'], total)}")
        
        print(f"\n{'='*60}")
        print(f"  SERVER METRICS")
        print(f"{'='*60}")
        try:
            with urllib.request.urlopen(URL_METRICS) as response:
                metrics = json.loads(response.read().decode())
                print(f"  Allowed (total):   {metrics.get('allowedRequests')}")
                print(f"  Rejected (total):  {metrics.get('rejectedRequests')}")
                avg = metrics.get('avgLatencyMs', 0)
                print(f"  Avg latency:       {avg:.3f} ms")
        except Exception as e:
            print(f"  Error fetching metrics: {e}")
        print(f"{'='*60}\n")

except KeyboardInterrupt:
    print("\n\nStopped by user. Final Stats:")
    total = global_results["200"] + global_results["429"] + global_results["other"]
    print(f"Total Requests: {total} (OK: {global_results['200']}, Rejected: {global_results['429']})")

