/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.google.android.apps.exposurenotification.roaming;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.hilt.Assisted;
import androidx.hilt.work.WorkerInject;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.Operation;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.ScheduledExecutor;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.work.WorkerStartupManager;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.threeten.bp.Duration;

public class CountryCheckingWorker extends ListenableWorker {

  private static final String TAG = "CountryCheckingWorker";
  private static final String WORKER_NAME = "CountryCheckingWorker";

  private final ExecutorService backgroundExecutor;
  private final CountryCodes countryCodes;
  private final WorkerStartupManager workerStartupManager;

  @WorkerInject
  public CountryCheckingWorker(
      @Assisted @NonNull Context context,
      @Assisted @NonNull WorkerParameters workerParams,
      @BackgroundExecutor ExecutorService backgroundExecutor,
      CountryCodes countryCodes,
      WorkerStartupManager workerStartupManager) {
    super(context, workerParams);
    this.backgroundExecutor = backgroundExecutor;
    this.countryCodes = countryCodes;
    this.workerStartupManager = workerStartupManager;
  }

  @NonNull
  @Override
  public ListenableFuture<Result> startWork() {
    return FluentFuture.from(
        workerStartupManager.getIsEnabledWithStartupTasks())
        .transformAsync(
            (isEnabled) -> {
              // Only continue if it is enabled.
              if (isEnabled) {
                countryCodes.updateDatabaseWithCurrentCountryCode();
                countryCodes.deleteObsoleteCountryCodes();
                return Futures.immediateFuture(Result.success());
              } else {
                // Stop here because things aren't enabled. Will still return successful though.
                return Futures.immediateFuture(Result.success());
              }
            },
            backgroundExecutor)
        .catching(
            Exception.class,
            x -> {
              Log.e(TAG, "Failure to check country code", x);
              return Result.failure();
            },
            backgroundExecutor);
  }

  public static Operation schedule(WorkManager workManager) {
    Log.d(TAG, "Scheduling country code checker");
    // WARNING: You must set ExistingPeriodicWorkPolicy.REPLACE if you want to change the params for
    //          previous app version users.
    return workManager.enqueueUniquePeriodicWork(WORKER_NAME, ExistingPeriodicWorkPolicy.KEEP,
        new PeriodicWorkRequest.Builder(CountryCheckingWorker.class, 6, TimeUnit.HOURS).build());
  }

  public static void cancel(WorkManager workManager) {
    Log.d(TAG, "Cancelling country code checker");
    workManager.cancelUniqueWork(WORKER_NAME);
  }

}
