(ns bricklaying.governor
  "BricklayingGovernor — the independent safety/scope layer gating
  every job-site scheduling/logistics proposal an advisor may make for
  a bricklaying crew. The governor never dispatches hardware itself,
  never performs masonry work on the job site, and never finalizes a
  masonry-work-execution decision (e.g. deciding to proceed with a
  specific wall-laying or mortar-joint step) or overrides a site
  safety officer's or foreman's judgment — those are permanently out
  of this actor's scope and remain a site safety officer's or
  foreman's exclusive judgment (README's 'Robotics premise': this
  actor coordinates JOB-SITE SCHEDULING/LOGISTICS ONLY — it never
  performs masonry work itself). Modeled on cloud-itonami-isco-3313's
  accountingsupport.governor (and closely on cloud-itonami-isco-7111's
  housebuilder.governor for the job-site-coordination domain shape).

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. worker provenance     — the crew member must be independently
                                verified/registered before any action.
    2. site provenance       — the job site must be independently
                                verified/registered before any action.
    3. no-actuation           — proposal :effect must be :propose (the
                                governor never dispatches hardware and
                                never performs masonry work itself; it
                                only gates what the advisor may
                                coordinate).
    4. closed op-allowlist    — only :log-work-record,
                                :schedule-crew-operation,
                                :flag-safety-concern and
                                :coordinate-supply-order may ever be
                                proposed; anything else is refused.
    5. scope-excluded action  — any proposal to directly finalize a
                                masonry-work-execution decision (e.g.
                                deciding to proceed with a specific
                                wall-laying or mortar-joint step), or
                                to override a site safety officer's or
                                foreman's judgment, is a hard,
                                permanent block (checked both against
                                the proposed :op and, defense-in-depth,
                                against the proposal's :rationale text
                                — matched as full finalization/
                                execution ACTION phrases such as
                                \"proceed with the masonry work\" /
                                \"authorize the wall construction\" /
                                \"override the foreman's safety
                                judgment\", never as bare nouns like
                                \"masonry\", \"wall\" or \"safety\", so
                                the check can never self-trip on the
                                advisor's own routine rationale text,
                                e.g. \"logged work record for worker
                                …\" or \"scheduled crew operation for
                                masonry task …\" or \"…routed for site
                                safety officer review\" — all three
                                legitimately contain those bare nouns
                                but none is a finalization action, and
                                all are exercised by
                                `governor-test/default-mock-advisor-proposals-never-self-trip-on-scope-exclusion`).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off
  regardless of confidence):
    6. :op :flag-safety-concern (a structural-integrity / falling-
                                material / wall-collapse / scaffold-
                                hazard / crew-fatigue concern always
                                escalates to a human, never
                                auto-commits).
    7. :op :coordinate-supply-order above `supply-cost-threshold`.
    8. low confidence (< `confidence-floor`)."
  (:require [clojure.string :as str]
            [bricklaying.store :as store]))

(def confidence-floor 0.6)
(def supply-cost-threshold 2000)

(def allowed-ops
  #{:log-work-record :schedule-crew-operation
    :flag-safety-concern :coordinate-supply-order})

;; Defense-in-depth: none of these ops are ever in `allowed-ops`
;; above, so they are already refused by the closed-allowlist check
;; below; they are named again here — as explicit finalization/
;; execution ACTIONS, never bare nouns — so a future allowlist edit
;; cannot silently re-open this specific out-of-scope path without
;; also touching this list.
(def ^:private scope-excluded-ops
  #{:finalize-masonry-work-decision :authorize-wall-construction
    :proceed-with-masonry-work :override-safety-officer-judgment
    :override-foreman-judgment})

;; Full finalization/execution ACTION phrases only — never bare nouns
;; ("masonry", "wall", "mortar", "safety", "foreman") — so this can
;; never match inside the mock advisor's own default rationale text
;; (which legitimately contains those bare nouns, e.g. "masonry task"
;; / "site safety officer review"). See
;; `governor-test/default-mock-advisor-proposals-never-self-trip-on-scope-exclusion`.
(def ^:private scope-excluded-phrases
  ["proceed with the masonry work" "proceed with the wall-laying work"
   "finalize the masonry work decision" "authorize the wall construction"
   "finalize the mortar-joint decision"
   "override the foreman's safety judgment"
   "override the safety officer's judgment"
   "override the site safety officer's judgment"
   "override foreman safety judgment"
   "override site safety officer judgment"])

(defn- contains-excluded-phrase? [s]
  (let [s (str/lower-case (or s ""))]
    (boolean (some #(str/includes? s %) scope-excluded-phrases))))

(defn- hard-violations [proposal worker-record site-record]
  (let [{:keys [op rationale]} proposal]
    (cond-> []
      (nil? worker-record)
      (conj {:rule :no-worker
             :detail "未登録 worker への提案は不可（worker record は独立して検証・登録済みでなければならない）"})

      (nil? site-record)
      (conj {:rule :no-site
             :detail "未登録 site への提案は不可（site record は独立して検証・登録済みでなければならない）"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation
             :detail "effect は :propose のみ許可（governor は現場作業を直接実行しない）"})

      (not (contains? allowed-ops op))
      (conj {:rule :unknown-op
             :detail (str op " は closed op-allowlist に無い — 提案不可")})

      (or (contains? scope-excluded-ops op) (contains-excluded-phrase? rationale))
      (conj {:rule :scope-excluded-action
             :detail "組積工事の実行判断（壁積み/目地施工等）の確定・site safety officer/foreman の判断の上書きは、この actor の権限外 — 常に永続ブロック"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `bricklaying.store/Store`. Pure — never
  mutates the store, never dispatches a site operation."
  [request _context proposal store]
  (let [worker-record (store/worker store (:worker-id request))
        site-record (some->> (:site-id proposal) (store/site store))
        hard (hard-violations proposal worker-record site-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        supply-order-over-threshold?
        (and (= :coordinate-supply-order (:op proposal))
             (number? (:cost proposal))
             (> (:cost proposal) supply-cost-threshold))
        always-risky? (or (= :flag-safety-concern (:op proposal))
                           supply-order-over-threshold?)]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
