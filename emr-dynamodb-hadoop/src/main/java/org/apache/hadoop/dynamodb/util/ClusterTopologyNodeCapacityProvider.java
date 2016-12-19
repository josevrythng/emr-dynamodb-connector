/**
 * Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file
 * except in compliance with the License. A copy of the License is located at
 *
 *     http://aws.amazon.com/apache2.0/
 *
 * or in the "LICENSE.TXT" file accompanying this file. This file is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under the License.
 */

package org.apache.hadoop.dynamodb.util;

import com.amazonaws.util.json.Jackson;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.mapred.JobConf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;

public class ClusterTopologyNodeCapacityProvider implements NodeCapacityProvider {
  private static final Log log = LogFactory.getLog(ClusterTopologyNodeCapacityProvider.class);

  private final JobConf conf;

  public ClusterTopologyNodeCapacityProvider(JobConf conf) {
    this.conf = conf;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int getCoreNodeMemoryMB() {
    boolean isCoreNodeSameInstanceTypeAsMaster = false;
    try {
      String jobFlowJsonString = readJobFlowJsonString();

      String masterInstanceType = extractInstanceType(jobFlowJsonString, "Master");
      String coreInstanceType = extractInstanceType(jobFlowJsonString, "Core");

      if (masterInstanceType != null && masterInstanceType.equals(coreInstanceType)) {
        isCoreNodeSameInstanceTypeAsMaster = true;
      }
    } catch (Exception e) {
      log.warn("Exception when trying to determine instance types", e);
    }

    /*
    conf.getInt("yarn.nodemanager.resource.memory-mb") yields the master node's total memory,
    which is not applicable
    unless the master and core nodes have the same instance type.

    conf.getInt("yarn.scheduler.maximum-allocation-mb" yields the maximum allocation for every
    container in a core node.
    This is usually smaller than the total available memory on a core node, so is only used as a
    fallback when the core
    node has a different instance type than the master.
     */
    if (isCoreNodeSameInstanceTypeAsMaster) {
      return conf.getInt("yarn.nodemanager.resource.memory-mb", 8192); // Default value from
      // yarn-default.xml
    }
    return conf.getInt("yarn.scheduler.maximum-allocation-mb", 8192); // Default value from
    // yarn-default.xml
  }

  /* An example of job-flow.json file:
  {
  "jobFlowId": "j-2AO77MNLG17NW",
  "jobFlowCreationInstant": 1429046932628,
  "instanceCount": 2,
  "masterInstanceId": "i-08dea4f4",
  "masterPrivateDnsName": "localhost",
  "masterInstanceType": "m1.medium",
  "slaveInstanceType": "m1.xlarge",
  "hadoopVersion": "2.4.0",
  "instanceGroups": [
  {
    "instanceGroupId": "ig-16NXM94TY33LB",
    "instanceGroupName": "CORE",
    "instanceRole": "Core",
    "marketType": "OnDemand",
    "instanceType": "m3.xlarge",
    "requestedInstanceCount": 1
  },
  {
    "instanceGroupId": "ig-2XQ29JGCTKLBL",
    "instanceGroupName": "MASTER",
    "instanceRole": "Master",
    "marketType": "OnDemand",
    "instanceType": "m1.medium",
    "requestedInstanceCount": 1
  }]
  }
  */
  String readJobFlowJsonString() throws IOException {
    return new String(Files.readAllBytes(Paths.get("/mnt/var/lib/info/job-flow.json")));
  }

  private String extractInstanceType(String jobFlowJsonString, String targetInstanceRole) throws
          Exception {

      JsonNode jobFlowJson = Jackson.jsonNodeOf(jobFlowJsonString);
    //JSONObject jobFlowJson = new JSONObject(jobFlowJsonString);
    //JSONArray instanceGroups = jobFlowJson.getJSONArray("instanceGroups");
      Iterator<JsonNode> instanceGroups = jobFlowJson.get("instanceGroups").iterator();

      while (instanceGroups.hasNext()) {
          JsonNode instanceGroup = instanceGroups.next();
          String instanceRole = instanceGroup.asText("instanceRole");
          if (targetInstanceRole.equalsIgnoreCase(instanceRole)) {
              String instanceType = instanceGroup.asText("instanceType");
              log.info(instanceRole + " instance type: " + instanceType);
              return instanceType;
          }
      }
      return null;
  }
}
