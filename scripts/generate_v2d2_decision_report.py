#!/usr/bin/env python3
"""Generate descriptive V2-D.2 decision inputs from evidence."""
from __future__ import annotations
import argparse,csv,json,statistics
from collections import defaultdict
from pathlib import Path
def props(p):
 d={}
 for raw in p.read_text(encoding="utf-8").splitlines():
  s=raw.strip()
  if s and not s.startswith("#") and "=" in s:
   k,v=s.split("=",1); d[k.strip()]=v.strip()
 return d
def rows(p):
 if not p or not p.is_file(): return []
 with p.open(encoding="utf-8",newline="") as h:return list(csv.DictReader(h))
def num(v):
 try:return float(v) if v not in (None,"") else None
 except ValueError:return None
def main():
 ap=argparse.ArgumentParser(); ap.add_argument("--environment",type=Path,required=True); ap.add_argument("--matrix-summary",type=Path); ap.add_argument("--soak",type=Path); ap.add_argument("--output-dir",type=Path,required=True); a=ap.parse_args()
 env=props(a.environment); rs=rows(a.matrix_summary); soak=json.loads(a.soak.read_text(encoding="utf-8")) if a.soak and a.soak.is_file() else {}
 alloc=defaultdict(list); pairs=defaultdict(dict); scen_thr=defaultdict(list); scen_p99=defaultdict(list); resil=defaultdict(list)
 for r in rs:
  score=num(r.get("score")); av=num(r.get("alloc_b_op"))
  if r["family"]=="payload" and av is not None: alloc[int(r["payload_bytes"])].append(av)
  if r["family"]=="security" and score is not None: pairs[(r["mode"],int(r["payload_bytes"]),int(r["connections"]),int(r["threads"]),r["benchmark"])][r["security"]]=score
  if r["family"]=="scenario":
   if r["mode"]=="thrpt" and score is not None: scen_thr[r["scenario"]].append(score)
   p=num(r.get("p99"))
   if r["mode"]=="sample" and p is not None: scen_p99[r["scenario"]].append(p)
  if r["family"]=="resilience" and score is not None: resil[(r["benchmark"],r["mode"])].append(score)
 tls=defaultdict(list)
 for (mode,payload,*_),v in pairs.items():
  if "PLAINTEXT" in v and "TLS" in v and v["PLAINTEXT"]!=0: tls[(mode,payload)].append((v["TLS"]/v["PLAINTEXT"]-1)*100)
 cpu=float(soak.get("processCpuCoresAverage",-1)); qps=float(soak.get("throughputOpsPerSecond",0)); soak["qpsPerCpuCore"]=qps/cpu if cpu>0 else None
 report={"schemaVersion":1,"evidenceClass":env.get("evidence_class","unknown"),"runnerId":env.get("runner_id","unknown"),"commit":env.get("commit","unknown"),"soak":soak,"allocationByPayload":[{"payloadBytes":k,"medianAllocationBytesPerOp":statistics.median(v),"samples":len(v)} for k,v in sorted(alloc.items())],"tlsRelativeDeltas":[{"mode":k[0],"payloadBytes":k[1],"medianTlsRelativeDeltaPercent":statistics.median(v),"minTlsRelativeDeltaPercent":min(v),"maxTlsRelativeDeltaPercent":max(v),"pairs":len(v)} for k,v in sorted(tls.items())],"scenarioSummary":[{"scenario":s,"medianThroughputOpsPerSecond":statistics.median(scen_thr[s]) if scen_thr[s] else None,"medianP99Micros":statistics.median(scen_p99[s]) if scen_p99[s] else None} for s in sorted(set(scen_thr)|set(scen_p99))],"resilienceSummary":[{"benchmark":k[0],"mode":k[1],"medianScore":statistics.median(v),"samples":len(v)} for k,v in sorted(resil.items())],"decisionState":{"bufferOwnership":"requires-human-review-of-fixed-hardware-evidence","futurePendingRequest":"requires-human-review-of-fixed-hardware-evidence","productionCapacityNumbers":"requires-controlled-evidence"}}
 a.output_dir.mkdir(parents=True,exist_ok=True); (a.output_dir/"decision-inputs.json").write_text(json.dumps(report,indent=2,sort_keys=True)+"\n",encoding="utf-8")
 lines=["# V2-D.2 Decision Inputs","",f"- Evidence class: `{report['evidenceClass']}`",f"- Runner ID: `{report['runnerId']}`",f"- Commit: `{report['commit']}`","","> Descriptive evidence only. Buffer ownership, Future/PendingRequest refactors, and Production SLOs still require engineering review."]
 if soak: lines += ["","## Soak","",f"- Throughput: {soak.get('throughputOpsPerSecond','-')} ops/s",f"- QPS/Core: {soak.get('qpsPerCpuCore','-')}",f"- p99: {soak.get('p99Micros','-')} us",f"- p99.9: {soak.get('p999Micros','-')} us",f"- Error rate: {soak.get('errorRate','-')}"]
 lines += ["","## Decision gates","","- Buffer ownership: inspect large-payload allocation/GC and tail-latency scaling.","- Future/PendingRequest: inspect small-payload allocation/op and resilience/cancellation cost.","- Capacity: only controlled fixed-hardware evidence may populate production numeric recommendations.",""]
 (a.output_dir/"decision-inputs.md").write_text("\n".join(lines),encoding="utf-8"); return 0
if __name__=="__main__": raise SystemExit(main())
