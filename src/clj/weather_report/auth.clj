(ns weather-report.auth
  (:require [com.stuartsierra.component :as component]
            [clj-ldap.client :as ldap]
            [clojure.string :as string]))


(defn parse-group [memberOf]
  (-> memberOf
      (string/split #",")
      first
      (string/split #"=")
      second))

(defn format-auth-info
  "extract info from Active Directory user, attributes based on visual scan"
  [{:keys [mail displayName msSFU30PosixMemberOf]}]
  (let [groups (map parse-group msSFU30PosixMemberOf)]
    {:email mail
     :display-name displayName
     :groups groups}))

(defn ldap-search [ldap-pool username password {:keys [attributes user-domain search-base search-attribute] :as search-info}]
  (let [conn           (ldap/get-connection ldap-pool)
        qualified-name (if user-domain (str username "@" user-domain) username)]
    (try
      (if (ldap/bind? conn qualified-name password)
        (first (ldap/search conn
                            search-base
                            {:filter     (str search-attribute "=" username)
                             :attributes attributes})))
      (finally (ldap/release-connection ldap-pool conn)))))

(defprotocol LDAPAuth
  (authenticate [this username password]))

(defrecord LDAP [conf]
  component/Lifecycle
  (start [cmp]
    (let [conn-info (get-in cmp [:conf :ldap :connection])
          search-info (get-in cmp [:conf :ldap :search])]
      (if conn-info
        (assoc cmp :pool (ldap/connect conn-info)
                   :search-info search-info)
        (assoc cmp :pool nil))))
  (stop [cmp]
        (if-let [pool (:pool cmp)]
          (do
            (ldap/close pool)
            (assoc cmp pool nil))
          cmp))
  LDAPAuth
  (authenticate [cmp username password]
    ;; search base is required, it is the container to start looking, usually
    ;; based on the domain
    (let [{:keys [attributes user-domain search-base search-attribute]
           :or {attributes [:mail]
                search-attribute "sAMAccountName"}
           :as search-info} (:search-info cmp)
          result (ldap-search (:pool cmp) username password search-info)]
      (if result
        (format-auth-info result)))))

(defrecord FakeLDAP [conf]
  component/Lifecycle
  (start [cmp] cmp)
  (stop [cmp] cmp)
  LDAPAuth
  (authenticate [cmp username password]
    (if (= username password)
      {:email "fake@example.com"
       :display-name username
       :groups ["test"]})))
