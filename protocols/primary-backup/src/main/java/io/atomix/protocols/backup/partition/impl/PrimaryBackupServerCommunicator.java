/*
 * Copyright 2017-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.protocols.backup.partition.impl;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.primitive.event.PrimitiveEvent;
import io.atomix.primitive.session.SessionId;
import io.atomix.protocols.backup.protocol.BackupEvent;
import io.atomix.protocols.backup.protocol.BackupRequest;
import io.atomix.protocols.backup.protocol.BackupResponse;
import io.atomix.protocols.backup.protocol.CloseRequest;
import io.atomix.protocols.backup.protocol.CloseResponse;
import io.atomix.protocols.backup.protocol.ExecuteRequest;
import io.atomix.protocols.backup.protocol.ExecuteResponse;
import io.atomix.protocols.backup.protocol.MetadataRequest;
import io.atomix.protocols.backup.protocol.MetadataResponse;
import io.atomix.protocols.backup.protocol.PrimaryBackupServerProtocol;
import io.atomix.protocols.backup.protocol.RestoreRequest;
import io.atomix.protocols.backup.protocol.RestoreResponse;

/**
 * Raft server protocol that uses a {@link ClusterCommunicationService}.
 */
public class PrimaryBackupServerCommunicator implements PrimaryBackupServerProtocol {
  private final PrimaryBackupMessageContext context;
  private final ClusterCommunicationService clusterCommunicator;

  public PrimaryBackupServerCommunicator(String prefix, ClusterCommunicationService clusterCommunicator) {
    this.context = new PrimaryBackupMessageContext(prefix);
    this.clusterCommunicator = Preconditions.checkNotNull(clusterCommunicator, "clusterCommunicator cannot be null");
  }

  @Override
  public CompletableFuture<BackupResponse> backup(MemberId memberId, BackupRequest request) {
    return clusterCommunicator.send(
        context.backupSubject,
        request,
        this::encode,
        bytes -> decode(bytes, BackupResponse::parseFrom),
        memberId);
  }

  @Override
  public CompletableFuture<RestoreResponse> restore(MemberId memberId, RestoreRequest request) {
    return clusterCommunicator.send(
        context.restoreSubject,
        request,
        this::encode,
        bytes -> decode(bytes, RestoreResponse::parseFrom),
        memberId);
  }

  @Override
  public void event(MemberId memberId, SessionId session, PrimitiveEvent event) {
    clusterCommunicator.unicast(
        context.eventSubject(session.id()),
        BackupEvent.newBuilder()
            .setType(event.type().id())
            .setValue(ByteString.copyFrom(event.value()))
            .build(),
        this::encode,
        memberId);
  }

  @Override
  public void registerExecuteHandler(Function<ExecuteRequest, CompletableFuture<ExecuteResponse>> handler) {
    clusterCommunicator.subscribe(
        context.executeSubject,
        bytes -> decode(bytes, ExecuteRequest::parseFrom),
        handler,
        this::encode);
  }

  @Override
  public void unregisterExecuteHandler() {
    clusterCommunicator.unsubscribe(context.executeSubject);
  }

  @Override
  public void registerBackupHandler(Function<BackupRequest, CompletableFuture<BackupResponse>> handler) {
    clusterCommunicator.subscribe(
        context.backupSubject,
        bytes -> decode(bytes, BackupRequest::parseFrom),
        handler,
        this::encode);
  }

  @Override
  public void unregisterBackupHandler() {
    clusterCommunicator.unsubscribe(context.backupSubject);
  }

  @Override
  public void registerRestoreHandler(Function<RestoreRequest, CompletableFuture<RestoreResponse>> handler) {
    clusterCommunicator.subscribe(
        context.restoreSubject,
        bytes -> decode(bytes, RestoreRequest::parseFrom),
        handler,
        this::encode);
  }

  @Override
  public void unregisterRestoreHandler() {
    clusterCommunicator.unsubscribe(context.restoreSubject);
  }

  @Override
  public void registerCloseHandler(Function<CloseRequest, CompletableFuture<CloseResponse>> handler) {
    clusterCommunicator.subscribe(
        context.closeSubject,
        bytes -> decode(bytes, CloseRequest::parseFrom),
        handler,
        this::encode);
  }

  @Override
  public void unregisterCloseHandler() {
    clusterCommunicator.unsubscribe(context.closeSubject);
  }

  @Override
  public void registerMetadataHandler(Function<MetadataRequest, CompletableFuture<MetadataResponse>> handler) {
    clusterCommunicator.subscribe(
        context.metadataSubject,
        bytes -> decode(bytes, MetadataRequest::parseFrom),
        handler,
        this::encode);
  }

  @Override
  public void unregisterMetadataHandler() {
    clusterCommunicator.unsubscribe(context.metadataSubject);
  }

  private interface Parser<T> {
    T parse(byte[] bytes) throws InvalidProtocolBufferException;
  }

  private byte[] encode(Message message) {
    return message.toByteArray();
  }

  private <T extends Message> T decode(byte[] bytes, Parser<T> parser) {
    try {
      return parser.parse(bytes);
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException(e);
    }
  }
}
