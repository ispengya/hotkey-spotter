import sys, json
from collections import defaultdict

path = sys.argv[1]
groups = defaultdict(lambda: {
    'rq': [], 'dq': [], 'cpu': [], 'mem': [], 'health': set(),
    'received': 0, 'dealt': 0, 'hot': 0, 'pushed': 0, 'pushFail': 0, 'pushRate': [],
})

for line in open(path, encoding='utf-8'):
    line = line.strip()
    if not line:
        continue
    try:
        d = json.loads(line)
    except Exception:
        continue
    g = d.get('_group', '?')
    v = groups[g]
    v['rq'].append(d.get('receivedQps', 0) or 0)
    v['dq'].append(d.get('dealtQps', 0) or 0)
    v['cpu'].append(d.get('processCpuLoad', -1) or -1)
    v['mem'].append(d.get('heapUsedMB', -1) or -1)
    v['health'].add(d.get('pipelineHealth', '?'))
    v['received'] = d.get('received', 0) or 0
    v['dealt'] = d.get('dealt', 0) or 0
    v['hot'] = d.get('hotKeyDetected', 0) or 0
    v['pushed'] = d.get('pushed', 0) or 0
    v['pushFail'] = d.get('pushFailed', 0) or 0
    pr = d.get('pushSuccessRate', -1)
    if pr is not None and pr != -1:
        v['pushRate'].append(pr)


def peak(x):
    return max(x) if x else 0


def avg(x):
    return sum(x) / len(x) if x else 0


print('samples=%d' % sum(len(v['rq']) for v in groups.values()))
for g in sorted(groups):
    v = groups[g]
    print('--- %s ---' % g)
    print('  receivedQps: peak=%8.0f avg=%8.0f' % (peak(v['rq']), avg(v['rq'])))
    print('  dealtQps:    peak=%8.0f avg=%8.0f' % (peak(v['dq']), avg(v['dq'])))
    print('  cpu%%:        peak=%6.1f avg=%6.1f' % (peak(v['cpu']), avg(v['cpu'])))
    print('  heapUsedMB:  peak=%6d avg=%6d' % (peak(v['mem']), avg(v['mem'])))
    print('  health: %s' % sorted(v['health']))
    print('  cumulative: received=%d dealt=%d hotKeyDetected=%d pushed=%d pushFail=%d pushRateAvg=%.2f%%' % (
        v['received'], v['dealt'], v['hot'], v['pushed'], v['pushFail'], avg(v['pushRate'])))
