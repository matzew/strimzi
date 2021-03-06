/*
 * Copyright 2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.systemtest;

import io.strimzi.test.ClusterOperator;
import io.strimzi.test.CmData;
import io.strimzi.test.ConnectCluster;
import io.strimzi.test.JUnitGroup;
import io.strimzi.test.KafkaCluster;
import io.strimzi.test.Namespace;
import io.strimzi.test.OpenShiftOnly;
import io.strimzi.test.Resources;
import io.strimzi.test.StrimziRunner;
import io.strimzi.test.k8s.Oc;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.strimzi.test.TestUtils.map;
import static org.junit.Assert.assertEquals;

@RunWith(StrimziRunner.class)
@Namespace(ConnectClusterIT.NAMESPACE)
@ClusterOperator
@KafkaCluster(name = ConnectClusterIT.KAFKA_CLUSTER_NAME)
public class ConnectClusterIT extends AbstractClusterIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectClusterIT.class);

    public static final String NAMESPACE = "connect-cluster-test";
    public static final String KAFKA_CLUSTER_NAME = "connect-tests";
    public static final String KAFKA_CONNECT_BOOTSTRAP_SERVERS = KAFKA_CLUSTER_NAME + "-kafka:9092";
    public static final String KAFKA_CONNECT_BOOTSTRAP_SERVERS_ESCAPED = KAFKA_CLUSTER_NAME + "-kafka\\:9092";
    public static final String CONNECT_CONFIG = "{\n" +
            "      \"bootstrap.servers\": \"" + KAFKA_CONNECT_BOOTSTRAP_SERVERS + "\",\n" +
            "      \"group.id\": \"my-connect-cluster\",\n" +
            "      \"offset.storage.topic\": \"my-connect-cluster-offsets\",\n" +
            "      \"config.storage.topic\": \"my-connect-cluster-configs\",\n" +
            "      \"status.storage.topic\": \"my-connect-cluster-status\",\n" +
            "      \"key.converter\": \"org.apache.kafka.connect.json.JsonConverter\",\n" +
            "      \"value.converter\": \"org.apache.kafka.connect.json.JsonConverter\",\n" +
            "      \"key.converter.schemas.enable\": true,\n" +
            "      \"value.converter.schemas.enable\": true,\n" +
            "      \"internal.key.converter\": \"org.apache.kafka.connect.json.JsonConverter\",\n" +
            "      \"internal.value.converter\": \"org.apache.kafka.connect.json.JsonConverter\",\n" +
            "      \"internal.key.converter.schemas.enable\": false,\n" +
            "      \"internal.value.converter.schemas.enable\": false,\n" +
            "      \"config.storage.replication.factor\": 3,\n" +
            "      \"offset.storage.replication.factor\": 3,\n" +
            "      \"status.storage.replication.factor\": 3\n" +
            "    }";

    private static final String EXPECTED_CONFIG = "group.id=my-connect-cluster\\n" +
            "key.converter=org.apache.kafka.connect.json.JsonConverter\\n" +
            "offset.storage.replication.factor=3\\n" +
            "internal.key.converter.schemas.enable=false\\n" +
            "config.storage.replication.factor=3\\n" +
            "value.converter=org.apache.kafka.connect.json.JsonConverter\\n" +
            "bootstrap.servers=" + KAFKA_CONNECT_BOOTSTRAP_SERVERS_ESCAPED + "\\n" +
            "key.converter.schemas.enable=true\\n" +
            "config.storage.topic=my-connect-cluster-configs\\n" +
            "status.storage.topic=my-connect-cluster-status\\n" +
            "offset.storage.topic=my-connect-cluster-offsets\\n" +
            "internal.key.converter=org.apache.kafka.connect.json.JsonConverter\\n" +
            "status.storage.replication.factor=3\\n" +
            "internal.value.converter.schemas.enable=false\\n" +
            "value.converter.schemas.enable=true\\n" +
            "internal.value.converter=org.apache.kafka.connect.json.JsonConverter\\n";

    @Test
    @JUnitGroup(name = "regression")
    @Resources(value = "../examples/templates/cluster-operator", asAdmin = true)
    @OpenShiftOnly
    public void testDeployConnectClusterViaTemplate() {
        Oc oc = (Oc) this.kubeClient;
        String clusterName = "openshift-my-connect-cluster";
        oc.newApp("strimzi-connect", map("CLUSTER_NAME", clusterName,
                "KAFKA_CONNECT_BOOTSTRAP_SERVERS", KAFKA_CONNECT_BOOTSTRAP_SERVERS));
        String deploymentName = clusterName + "-connect";
        oc.waitForDeployment(deploymentName);
        oc.deleteByName("cm", clusterName);
        oc.waitForResourceDeletion("deployment", deploymentName);
    }

    @Test
    @JUnitGroup(name = "acceptance")
    @ConnectCluster(name = "my-cluster", connectConfig = CONNECT_CONFIG)
    public void testDeployUndeploy() {
        LOGGER.info("Looks like the connect cluster my-cluster deployed OK");

        String podName = kubeClient.list("Pod").stream().filter(n -> n.startsWith("my-cluster-connect-")).findFirst().get();
        String kafkaPodJson = kubeClient.getResourceAsJson("pod", podName);

        assertEquals(EXPECTED_CONFIG.replaceAll("\\p{P}", ""), getValueFromJson(kafkaPodJson,
                globalVariableJsonPathBuilder("KAFKA_CONNECT_USER_CONFIGURATION")));
    }

    @Test
    @JUnitGroup(name = "acceptance")
    @ConnectCluster(name = "jvm-resource", connectConfig = CONNECT_CONFIG,
        nodes = 1,
        config = {
                @CmData(key = "resources", value = "{ \"limits\": {\"memory\": \"400M\", \"cpu\": 2}, " +
                        "\"requests\": {\"memory\": \"300M\", \"cpu\": 1} }"),
                @CmData(key = "jvmOptions", value = "{\"-Xmx\": \"200m\", \"-Xms\": \"200m\"}")
        })
    public void testJvmAndResources() {
        String podName = kubeClient.list("Pod").stream().filter(n -> n.startsWith("jvm-resource-connect-")).findFirst().get();
        assertResources(NAMESPACE, podName,
                "400M", "2", "300M", "1");
        assertExpectedJavaOpts(podName,
                "-Xmx200m", "-Xms200m");
    }

}
