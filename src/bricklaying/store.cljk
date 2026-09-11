(ns bricklaying.store
  "SSoT for the ISCO-08 7112 masonry job-site scheduling/logistics
  coordination actor (itonami actor pattern, ADR-2607121000 / CLAUDE.md
  Actors section; README's 'Robotics premise' — a masonry job-site
  scheduling/logistics coordination robot performs crew scheduling,
  task/materials-usage/progress-record logging and masonry-materials
  supply-order coordination for a bricklaying crew under this
  advisor/governor pair, which never dispatches hardware itself, never
  performs masonry work itself, and never finalizes a
  masonry-work-execution decision (e.g. a specific wall-laying or
  mortar-joint step) or overrides a site safety officer's/foreman's
  judgment — those remain the site safety officer's/foreman's
  exclusive judgment). Modeled on cloud-itonami-isco-3313's
  accountingsupport.store (and closely on cloud-itonami-isco-7111's
  housebuilder.store for the job-site-coordination domain shape).

  Domain:

    worker  — a registered bricklaying crew member
              (:worker-id, :name)
    site    — a registered masonry job site {:site-id :name
              :max-supply-cost number}. `:max-supply-cost` is an
              informational registered ceiling used only to decide
              whether a `:coordinate-supply-order` proposal escalates
              to human sign-off (the governor never blocks a
              within-threshold order outright; it only decides
              commit vs. escalate).
    record  — a committed operating record (a logged
              task/materials-usage/progress entry, a scheduled crew
              operation, a flagged safety concern, or a coordinated
              supply order) — written ONLY via commit-record!.
    ledger  — append-only audit trail, commit or hold.")

(defprotocol Store
  (worker [s worker-id])
  (site [s site-id])
  (records-of [s worker-id])
  (ledger [s])
  (register-worker! [s w])
  (register-site! [s st])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (worker [_ worker-id] (get-in @a [:workers worker-id]))
  (site [_ site-id] (get-in @a [:sites site-id]))
  (records-of [_ worker-id] (filter #(= worker-id (:worker-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-worker! [s w]
    (swap! a assoc-in [:workers (:worker-id w)] w) s)
  (register-site! [s st]
    (swap! a assoc-in [:sites (:site-id st)] st) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:workers {} :sites {} :records [] :ledger []}
                                    seed)))))
