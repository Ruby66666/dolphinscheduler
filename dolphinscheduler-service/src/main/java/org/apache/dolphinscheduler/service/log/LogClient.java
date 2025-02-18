/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dolphinscheduler.service.log;

import static org.apache.dolphinscheduler.common.constants.Constants.APPID_COLLECT;
import static org.apache.dolphinscheduler.common.constants.Constants.DEFAULT_COLLECT_WAY;

import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.common.utils.NetUtils;
import org.apache.dolphinscheduler.common.utils.PropertyUtils;
import org.apache.dolphinscheduler.plugin.task.api.utils.LogUtils;
import org.apache.dolphinscheduler.remote.NettyRemotingClient;
import org.apache.dolphinscheduler.remote.command.Command;
import org.apache.dolphinscheduler.remote.command.log.GetAppIdRequestCommand;
import org.apache.dolphinscheduler.remote.command.log.GetAppIdResponseCommand;
import org.apache.dolphinscheduler.remote.command.log.GetLogBytesRequestCommand;
import org.apache.dolphinscheduler.remote.command.log.GetLogBytesResponseCommand;
import org.apache.dolphinscheduler.remote.command.log.RemoveTaskLogRequestCommand;
import org.apache.dolphinscheduler.remote.command.log.RemoveTaskLogResponseCommand;
import org.apache.dolphinscheduler.remote.command.log.RollViewLogRequestCommand;
import org.apache.dolphinscheduler.remote.command.log.RollViewLogResponseCommand;
import org.apache.dolphinscheduler.remote.command.log.ViewLogRequestCommand;
import org.apache.dolphinscheduler.remote.command.log.ViewLogResponseCommand;
import org.apache.dolphinscheduler.remote.exceptions.RemotingException;
import org.apache.dolphinscheduler.remote.factory.NettyRemotingClientFactory;
import org.apache.dolphinscheduler.remote.utils.Host;

import java.util.List;

import javax.annotation.Nullable;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LogClient implements AutoCloseable {

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private final NettyRemotingClient client;

    private static final long LOG_REQUEST_TIMEOUT = 10 * 1000L;

    public LogClient() {
        client = NettyRemotingClientFactory.buildNettyRemotingClient();
    }

    /**
     * roll view log
     *
     * @param host host
     * @param port port
     * @param path path
     * @param skipLineNum skip line number
     * @param limit limit
     * @return log content
     */
    public String rollViewLog(String host, int port, String path, int skipLineNum, int limit) {
        log.info("Roll view log from host : {}, port : {}, path {}, skipLineNum {} ,limit {}", host, port, path,
                skipLineNum, limit);
        RollViewLogRequestCommand request = new RollViewLogRequestCommand(path, skipLineNum, limit);
        final Host address = new Host(host, port);
        try {
            Command command = request.convert2Command();
            Command response = client.sendSync(address, command, LOG_REQUEST_TIMEOUT);
            if (response != null) {
                RollViewLogResponseCommand rollReviewLog =
                        JSONUtils.parseObject(response.getBody(), RollViewLogResponseCommand.class);
                return rollReviewLog.getMsg();
            }
            return "Roll view log response is null";
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error(
                    "Roll view log from host : {}, port : {}, path {}, skipLineNum {} ,limit {} error, the current thread has been interrupted",
                    host, port, path, skipLineNum, limit, ex);
            return "Roll view log error: " + ex.getMessage();
        } catch (Exception e) {
            log.error("Roll view log from host : {}, port : {}, path {}, skipLineNum {} ,limit {} error", host, port,
                    path, skipLineNum, limit, e);
            return "Roll view log error: " + e.getMessage();
        }
    }

    /**
     * view log
     *
     * @param host host
     * @param port port
     * @param path path
     * @return log content
     */
    public String viewLog(String host, int port, String path) {
        log.info("View log from host: {}, port: {}, logPath: {}", host, port, path);
        ViewLogRequestCommand request = new ViewLogRequestCommand(path);
        final Host address = new Host(host, port);
        try {
            if (NetUtils.getHost().equals(host)) {
                return LogUtils.readWholeFileContentFromLocal(request.getPath());
            } else {
                Command command = request.convert2Command();
                Command response = this.client.sendSync(address, command, LOG_REQUEST_TIMEOUT);
                if (response != null) {
                    ViewLogResponseCommand viewLog =
                            JSONUtils.parseObject(response.getBody(), ViewLogResponseCommand.class);
                    return viewLog.getMsg();
                }
                return "View log response is null";
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("View log from host: {}, port: {}, logPath: {} error, the current thread has been interrupted",
                    host, port, path, ex);
            return "View log error: " + ex.getMessage();
        } catch (Exception e) {
            log.error("View log from host: {}, port: {}, logPath: {} error", host, port, path, e);
            return "View log error: " + e.getMessage();
        }
    }

    /**
     * get log size
     *
     * @param host host
     * @param port port
     * @param path log path
     * @return log content bytes
     */
    public byte[] getLogBytes(String host, int port, String path) {
        log.info("Get log bytes from host: {}, port: {}, logPath {}", host, port, path);
        GetLogBytesRequestCommand request = new GetLogBytesRequestCommand(path);
        final Host address = new Host(host, port);
        try {
            Command command = request.convert2Command();
            Command response = this.client.sendSync(address, command, LOG_REQUEST_TIMEOUT);
            if (response != null) {
                GetLogBytesResponseCommand getLog =
                        JSONUtils.parseObject(response.getBody(), GetLogBytesResponseCommand.class);
                return getLog.getData() == null ? EMPTY_BYTE_ARRAY : getLog.getData();
            }
            return EMPTY_BYTE_ARRAY;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error(
                    "Get logSize from host: {}, port: {}, logPath: {} error, the current thread has been interrupted",
                    host, port, path, ex);
            return EMPTY_BYTE_ARRAY;
        } catch (Exception e) {
            log.error("Get logSize from host: {}, port: {}, logPath: {} error", host, port, path, e);
            return EMPTY_BYTE_ARRAY;
        }
    }

    /**
     * remove task log
     *
     * @param host host
     * @param path path
     */
    public void removeTaskLog(@NonNull Host host, String path) {
        log.info("Begin remove task log from host: {} logPath {}", host, path);
        RemoveTaskLogRequestCommand request = new RemoveTaskLogRequestCommand(path);
        try {
            Command command = request.convert2Command();
            client.sendAsync(host, command, LOG_REQUEST_TIMEOUT, responseFuture -> {
                if (responseFuture.getCause() != null) {
                    log.error("Remove task log from host: {} logPath {} error, meet an unknown exception", host,
                            path, responseFuture.getCause());
                    return;
                }
                Command response = responseFuture.getResponseCommand();
                if (response == null) {
                    log.error("Remove task log from host: {} logPath {} error, response is null", host, path);
                    return;
                }
                RemoveTaskLogResponseCommand removeTaskLogResponse =
                        JSONUtils.parseObject(response.getBody(), RemoveTaskLogResponseCommand.class);
                if (removeTaskLogResponse.getStatus()) {
                    log.info("Success remove task log from host: {} logPath {}", host, path);
                } else {
                    log.error("Remove task log from host: {} logPath {} error", host, path);
                }
            });
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            log.error("Remove task log from host: {} logPath {} error, the current thread has been interrupted",
                    host,
                    path, interruptedException);
        } catch (Exception e) {
            log.error("Remove task log from host: {},  logPath: {} error", host, path, e);
        }
    }

    public @Nullable List<String> getAppIds(@NonNull String host, int port, @NonNull String taskLogFilePath,
                                            @NonNull String taskAppInfoPath) throws RemotingException, InterruptedException {
        log.info("Begin to get appIds from worker: {}:{} taskLogPath: {}, taskAppInfoPath: {}", host, port,
                taskLogFilePath, taskAppInfoPath);
        final Host workerAddress = new Host(host, port);
        List<String> appIds = null;
        if (NetUtils.getHost().equals(host)) {
            appIds = LogUtils.getAppIds(taskLogFilePath, taskAppInfoPath,
                    PropertyUtils.getString(APPID_COLLECT, DEFAULT_COLLECT_WAY));
        } else {
            final Command command = new GetAppIdRequestCommand(taskLogFilePath, taskAppInfoPath).convert2Command();
            Command response = this.client.sendSync(workerAddress, command, LOG_REQUEST_TIMEOUT);
            if (response != null) {
                GetAppIdResponseCommand responseCommand =
                        JSONUtils.parseObject(response.getBody(), GetAppIdResponseCommand.class);
                appIds = responseCommand.getAppIds();
            }
        }
        log.info("Get appIds: {} from worker: {}:{} taskLogPath: {}, taskAppInfoPath: {}", appIds, host, port,
                taskLogFilePath, taskAppInfoPath);
        return appIds;
    }

    @Override
    public void close() {
        this.client.close();
        log.info("LogClientService closed");
    }

}
