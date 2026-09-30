#!/usr/bin/env python3
"""Validate completeness and provenance of Peach RPC V2-D.2 evidence."""
from __future__ import annotations
import argparse,csv,json
from pathlib import Path
RESILIENCE={"retryBudgetAcquire","circuitClosedAcquireAndSuccess","circuitOpenReject","outlierHealthyRead","outlierEjectedRead","outlierFailureAccounting"}
PROFILES={
"smoke":({"payloads":{256},"shards":{1},"threads":{1},"modes":{"sample"},"scenarios":{"NOOP"},"scenario_shards":{1},"scenario_threads":{1},"security_modes":{"PLAINTEXT"},"security_payloads":{256},"security_shards":{1},"security_threads":{1},"resilience_threads":{1}}),
"standard":({"payloads":{64,1024,16384},"shards":{1,4},"threads":{1,64,256},"modes":{"sample","thrpt"},"scenarios":{"NOOP","CPU","BLOCKING","SLOW_PROVIDER","OVERLOAD"},"scenario_shards":{1,4},"scenario_threads":{1,64,256},"security_modes":{"PLAINTEXT","TLS"},"security_payloads":{256,16384},"security_shards":{1,4},"security_threads":{1,64},"resilience_threads":{1,64,256}}),
"full":({"payloads":{64,256,1024,16384,1048576},"shards":{1,2,4,8},"threads":{1,16,64,256,1024},"modes":{"sample","thrpt"},"scenarios":{"NOOP","CPU","BLOCKING","SLOW_PROVIDER","OVERLOAD"},"scenario_shards":{1,4},"scenario_threads":{1,16,64,256,1024},"security_modes":{"PLAINTEXT","TLS"},"security_payloads":{64,256,1024,16384,1048576},"security_shards":{1,2,4,8},"security_threads":{1,16,64,256},"resilience_threads":{1,16,64,256,1024}})}
def props(p):
 d={}
 for raw in p.read_text(encoding="utf-8").splitlines():
  s=raw.strip()
  if s and not s.startswith("#") and "=" in s:
   k,v=s.split("=",1); d[k.strip()]=v.strip()
 return d
def rows(p):
 with p.open(encoding="utf-8",newline="") as h:return list(csv.DictReader(h))
def req(ok,msg,errs):
 if not ok: errs.append(msg)
def validate_matrix(root,profile,errs):
 summary=root/"summary.csv"; req(summary.is_file(),f"Missing matrix summary: {summary}",errs)
 if not summary.is_file(): return {}
 failures=root/"failures.csv"
 if failures.is_file():
  lines=[x for x in failures.read_text(encoding="utf-8").splitlines() if x.strip()]
  req(not lines,"Matrix contains failed capacity points: "+"; ".join(lines[:10]),errs)
 data=rows(summary); p=PROFILES[profile]
 payload={(r["mode"],int(r["payload_bytes"]),int(r["connections"]),int(r["threads"])) for r in data if r["family"]=="payload"}
 scenario={(r["scenario"],r["mode"],int(r["connections"]),int(r["threads"])) for r in data if r["family"]=="scenario"}
 security={(r["security"],r["mode"],int(r["payload_bytes"]),int(r["connections"]),int(r["threads"])) for r in data if r["family"]=="security"}
 resilience={(r["benchmark"],r["mode"],int(r["threads"])) for r in data if r["family"]=="resilience"}
 ep={(m,pl,s,t) for m in p["modes"] for pl in p["payloads"] for s in p["shards"] for t in p["threads"]}
 es={(sc,m,s,t) for sc in p["scenarios"] for m in p["modes"] for s in p["scenario_shards"] for t in p["scenario_threads"]}
 ec={(sec,m,pl,s,t) for sec in p["security_modes"] for m in p["modes"] for pl in p["security_payloads"] for s in p["security_shards"] for t in p["security_threads"]}
 er={(b,m,t) for b in RESILIENCE for m in p["modes"] for t in p["resilience_threads"]}
 req(not(ep-payload),f"Missing payload matrix points: {len(ep-payload)}",errs); req(not(es-scenario),f"Missing scenario matrix points: {len(es-scenario)}",errs); req(not(ec-security),f"Missing security matrix points: {len(ec-security)}",errs); req(not(er-resilience),f"Missing resilience matrix points: {len(er-resilience)}",errs)
 req(not[r for r in data if r["family"] in {"payload","scenario","security","resilience"} and not r.get("alloc_b_op")],"Rows without gc.alloc.rate.norm evidence",errs)
 return {"rows":len(data),"payload_points":len(payload),"scenario_points":len(scenario),"security_points":len(security),"resilience_points":len(resilience)}
def main():
 ap=argparse.ArgumentParser(); ap.add_argument("--environment",type=Path,required=True); ap.add_argument("--matrix-dir",type=Path); ap.add_argument("--soak",type=Path); ap.add_argument("--profile",choices=PROFILES,default="full"); ap.add_argument("--require-matrix",action="store_true"); ap.add_argument("--require-soak",action="store_true"); ap.add_argument("--require-controlled",action="store_true"); ap.add_argument("--min-soak-seconds",type=float,default=0); ap.add_argument("--min-concurrency",type=int,default=1); ap.add_argument("--output-dir",type=Path,required=True); a=ap.parse_args()
 errs=[]; req(a.environment.is_file(),f"Missing environment file: {a.environment}",errs); env=props(a.environment) if a.environment.is_file() else {}
 if a.require_controlled:
  req(env.get("evidence_class")=="controlled","Controlled evidence requires evidence_class=controlled",errs); req(env.get("runner_id") not in {"",None,"unknown","github-hosted-ephemeral"},"Controlled evidence requires a stable runner_id",errs); req(a.profile=="full","Controlled evidence requires profile=full",errs)
 matrix=validate_matrix(a.matrix_dir,a.profile,errs) if a.require_matrix and a.matrix_dir else {}
 soak={}
 if a.require_soak and a.soak:
  req(a.soak.is_file(),f"Missing soak JSON: {a.soak}",errs)
  if a.soak.is_file():
   soak=json.loads(a.soak.read_text(encoding="utf-8")); req(float(soak.get("durationSeconds",0))>=a.min_soak_seconds,f"Soak duration below {a.min_soak_seconds}s",errs); req(int(soak.get("concurrency",0))>=a.min_concurrency,f"Soak concurrency below {a.min_concurrency}",errs); req(int(soak.get("successes",0))>0,"Soak has no successful RPC calls",errs)
   if env.get("commit") not in {"",None,"unknown"}: req(soak.get("commit")==env.get("commit"),"Soak commit does not match environment commit",errs)
 a.output_dir.mkdir(parents=True,exist_ok=True); status="PASS" if not errs else "FAIL"; report={"schemaVersion":1,"status":status,"environment":env,"matrix":matrix,"soak":{k:soak.get(k) for k in ("concurrency","durationSeconds","throughputOpsPerSecond","p99Micros","errors","errorRate","processCpuCoresAverage") if k in soak},"errors":errs}; (a.output_dir/"validation-report.json").write_text(json.dumps(report,indent=2,sort_keys=True)+"\n",encoding="utf-8"); (a.output_dir/"validation-report.md").write_text("# V2-D.2 Evidence Validation\n\n**Status:** "+status+"\n"+("\n".join(f"- {e}" for e in errs) if errs else "\n> Structural evidence validation passed; this does not itself establish a Production SLO.\n"),encoding="utf-8")
 if errs:
  [print("ERROR: "+e) for e in errs]; return 1
 print("V2-D.2 evidence validation passed"); return 0
if __name__=="__main__": raise SystemExit(main())
