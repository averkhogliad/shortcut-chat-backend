helm install users-pg-data ./mount-volume -f ./users.pg-data-volume.values.yaml
helm install grafana-data ./mount-volume -f ./grafana.volume.values.yaml
helm install prometheus-data ./mount-volume -f ./prometheus.volume.values.yaml


helm install users-db ./postgres-db -f ./users.pg.values.yaml
# OR
helm install users-db oci://registry-1.docker.io/bitnamicharts/postgresql -f ./users.pg-bitnami.values.yaml

kubectl port-forward svc/users-db 15432:5432


helm install kafka oci://registry-1.docker.io/bitnamicharts/kafka -f kafka-bitnami.values.yaml

kubectl port-forward svc/kafka 9092:9092


helm install users-app ./users-app -f ./users.app.values.yaml

kubectl port-forward svc/users-app 8000:8080


helm install redis bitnami/redis -f ./redis.values.yaml

kubectl port-forward svc/redis-master 16379:6379


helm install mail-sender ./mail-sender -f ./mail-sender.values.yaml

kubectl port-forward svc/mail-sender 9099:8080


helm install kafka-ui kafka-ui/kafka-ui -f ./kafka-ui.values.yaml

kubectl port-forward svc/kafka-ui 3000:80


helm install loki grafana/loki  -f ./loki.yaml


helm install grafana grafana/grafana -f ./grafana.values.yaml

kubectl port-forward svc/grafana 9000:80


helm install prometheus prometheus-db -f .\prometheus.db.values.yaml

#helm install prometheus prometheus-community/prometheus -f .\prometheus.values.yaml

kubectl port-forward svc/grafana 9100:80

