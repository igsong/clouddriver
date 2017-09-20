/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class KubernetesManifestAnnotater {
  private static final String SPINNAKER_ANNOTATION = "spinnaker.io";
  private static final String RELATIONSHIP_ANNOTATION_PREFIX = "relationships." + SPINNAKER_ANNOTATION;
  private static final String ARTIFACT_ANNOTATION_PREFIX = "artifacts." + SPINNAKER_ANNOTATION;
  private static final String LOAD_BALANCERS = RELATIONSHIP_ANNOTATION_PREFIX + "/loadBalancers";
  private static final String SECURITY_GROUPS = RELATIONSHIP_ANNOTATION_PREFIX + "/securityGroups";
  private static final String CLUSTER = RELATIONSHIP_ANNOTATION_PREFIX + "/cluster";
  private static final String APPLICATION = RELATIONSHIP_ANNOTATION_PREFIX + "/application";
  private static final String TYPE = ARTIFACT_ANNOTATION_PREFIX + "/type";
  private static final String NAME = ARTIFACT_ANNOTATION_PREFIX + "/name";
  private static final String VERSION = ARTIFACT_ANNOTATION_PREFIX + "/version";

  private static ObjectMapper objectMapper = new ObjectMapper();

  private static void storeAnnotation(Map<String, String> annotations, String key, Object value) {
    if (value == null) {
      return;
    }

    try {
      annotations.put(key, objectMapper.writeValueAsString(value));
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Illegal annotation value for '" + key + "': " + e);
    }
  }

  private static <T> T getAnnotation(Map<String, String> annotations, String key, TypeReference<T> typeReference) {
    String value = annotations.get(key);
    if (value == null) {
      return null;
    }

    try {
      return objectMapper.readValue(value, typeReference);
    } catch (IOException e) {
      throw new IllegalArgumentException("Illegally annotated resource for '" + key + "': " + e);
    }
  }

  public static void annotateManifest(KubernetesManifest manifest, KubernetesAugmentedManifest.Metadata metadata) {
    Map<String, String> annotations = manifest.getAnnotations();
    annotateManifest(annotations, metadata.getRelationships());
    annotateManifest(annotations, metadata.getArtifact());

    manifest.getSpecTemplateAnnotations().flatMap(a -> {
      annotateManifest(a, metadata.getRelationships());
      return Optional.empty();
    });
  }

  private static void annotateManifest(Map<String, String> annotations, KubernetesManifestSpinnakerRelationships relationships) {
    if (relationships == null) {
      throw new IllegalArgumentException("Every resource deployed via spinnaker must be assigned relationships");
    }

    storeAnnotation(annotations, LOAD_BALANCERS, relationships.getLoadBalancers());
    storeAnnotation(annotations, SECURITY_GROUPS, relationships.getSecurityGroups());
    storeAnnotation(annotations, CLUSTER, relationships.getCluster());
    storeAnnotation(annotations, APPLICATION, relationships.getApplication());
  }

  private static void annotateManifest(Map<String, String> annotations, Artifact artifact) {
    if (artifact == null) {
      log.warn("Unknown artifact type & name");
      return;
    }

    storeAnnotation(annotations, TYPE, artifact.getType());
    storeAnnotation(annotations, NAME, artifact.getName());
    storeAnnotation(annotations, VERSION, artifact.getVersion());
  }

  public static KubernetesManifestSpinnakerRelationships getManifestRelationships(KubernetesManifest manifest) {
    Map<String, String> annotations = manifest.getAnnotations();

    return new KubernetesManifestSpinnakerRelationships()
        .setLoadBalancers(getAnnotation(annotations, LOAD_BALANCERS, new TypeReference<List<String>>() {}))
        .setSecurityGroups(getAnnotation(annotations, SECURITY_GROUPS, new TypeReference<List<String>>() {}))
        .setCluster(getAnnotation(annotations, CLUSTER, new TypeReference<String>() {}))
        .setApplication(getAnnotation(annotations, APPLICATION, new TypeReference<String>() {}));
  }

  public static Artifact getArtifact(KubernetesManifest manifest) {
    Map<String, String> annotations = manifest.getAnnotations();

    return Artifact.builder()
        .type(getAnnotation(annotations, TYPE, new TypeReference<String>() {}))
        .name(getAnnotation(annotations, NAME, new TypeReference<String>() {}))
        .version(getAnnotation(annotations, VERSION, new TypeReference<String>() {}))
        .build();
  }
}