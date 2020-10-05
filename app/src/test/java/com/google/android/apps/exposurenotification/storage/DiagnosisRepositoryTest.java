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

package com.google.android.apps.exposurenotification.storage;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.HasSymptoms;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.Shared;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TestResult;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TravelStatus;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.apps.exposurenotification.testsupport.InMemoryDb;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.threeten.bp.LocalDate;

/**
 * Tests for operations in {@link DiagnosisRepository}, which serves to also test
 * {@link DiagnosisDao} which it wraps.
 */
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
@UninstallModules({DbModule.class, RealTimeModule.class})
public class DiagnosisRepositoryTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  @BindValue
  ExposureNotificationDatabase db = InMemoryDb.create();
  @BindValue
  Clock clock = new FakeClock();

  @Inject
  DiagnosisRepository diagnosisRepo;

  @Before
  public void setUp() {
    rules.hilt().inject();
  }

  @Test
  public void getById_forNonExistentId_shouldReturnNull() throws Exception {
    // Create a record
    long newId = diagnosisRepo.upsertAsync(
        DiagnosisEntity.newBuilder().setVerificationCode("code1").build())
        .get();

    // But read a different ID and assert nothing is returned.
    long otherId = newId + 1;
    assertThat(diagnosisRepo.getById(otherId).get()).isNull();
  }

  @Test
  public void upsert_shouldCreateNewEntity_andGetById_shouldReturnIt() throws Exception {
    DiagnosisEntity diagnosis = DiagnosisEntity.newBuilder()
        .setVerificationCode("code1")
        .setCreatedTimestampMs(42L)
        .setSharedStatus(Shared.SHARED)
        .setLongTermToken("token")
        .setCertificate("cert")
        .setRevisionToken("revisionToken")
        .setOnsetDate(LocalDate.of(1, 2, 3))
        .setHasSymptoms(HasSymptoms.UNSET)
        .setTestResult(TestResult.LIKELY)
        .setTravelStatus(TravelStatus.TRAVELED)
        .build();

    long newId = diagnosisRepo.upsertAsync(diagnosis).get();
    DiagnosisEntity readBack = diagnosisRepo.getById(newId).get();

    assertThat(readBack).isNotNull();
    assertEqualIgnoringId(readBack, diagnosis);
  }

  @Test
  public void upsert_withNewEntity_missingCreationTime_shouldSetCreationTime() throws Exception {
    DiagnosisEntity diagnosis = DiagnosisEntity.newBuilder().build();

    long newId = diagnosisRepo.upsertAsync(diagnosis).get();
    DiagnosisEntity readBack = diagnosisRepo.getById(newId).get();

    DiagnosisEntity expected = DiagnosisEntity.newBuilder()
        .setCreatedTimestampMs(clock.now().toEpochMilli())
        .build();
    assertThat(readBack).isNotNull();
    assertEqualIgnoringId(readBack, expected);
  }

  @Test
  public void upsert_shouldUpdateExistingEntity() throws Exception {
    // Create and read back one record.
    long newId = diagnosisRepo.upsertAsync(
        DiagnosisEntity.newBuilder().setVerificationCode("code1").build()).get();
    DiagnosisEntity readBack = diagnosisRepo.getById(newId).get();

    // Mutate all the fields!
    DiagnosisEntity mutated = readBack.toBuilder()
        .setCreatedTimestampMs(42L)
        .setSharedStatus(Shared.SHARED)
        .setLongTermToken("token")
        .setCertificate("cert")
        .setRevisionToken("revisionToken")
        .setOnsetDate(LocalDate.of(1, 2, 3))
        .setHasSymptoms(HasSymptoms.YES)
        .setTestResult(TestResult.LIKELY)
        .setTravelStatus(TravelStatus.NOT_TRAVELED)
        .build();
    diagnosisRepo.upsertAsync(mutated);

    // Read it back again and it should have the token.
    DiagnosisEntity readAgain = diagnosisRepo.getById(newId).get();
    assertThat(readAgain).isEqualTo(mutated);
  }

  @Test
  public void upsertAsync_shouldCreateNewEntity() throws Exception {
    // Create a new diagnosis
    DiagnosisEntity diagnosis = DiagnosisEntity.newBuilder()
        .setVerificationCode("code1")
        .setCreatedTimestampMs(42L)
        .setSharedStatus(Shared.SHARED)
        .setLongTermToken("token")
        .setCertificate("cert")
        .setRevisionToken("revisionToken")
        .setOnsetDate(LocalDate.of(1, 2, 3))
        .setHasSymptoms(HasSymptoms.UNSET)
        .setTestResult(TestResult.LIKELY)
        .build();
    Long newId = diagnosisRepo.upsertAsync(diagnosis).get();

    // Then read it back and verify.
    DiagnosisEntity readBack = diagnosisRepo.getById(newId).get();
    assertEqualIgnoringId(readBack, diagnosis);
  }

  @Test
  public void upsertAsync_shouldUpdateExistingEntity() throws Exception {
    // Create and read back one record.
    long newId = diagnosisRepo.upsertAsync(
        DiagnosisEntity.newBuilder().setVerificationCode("code1").build()).get();
    DiagnosisEntity readBack = diagnosisRepo.getById(newId).get();

    // Mutate all the fields!
    DiagnosisEntity mutated = readBack.toBuilder()
        .setCreatedTimestampMs(42L)
        .setSharedStatus(Shared.SHARED)
        .setLongTermToken("token")
        .setCertificate("cert")
        .setRevisionToken("revisionToken")
        .setOnsetDate(LocalDate.of(1, 2, 3))
        .setHasSymptoms(HasSymptoms.YES)
        .setTestResult(TestResult.LIKELY)
        .setTravelStatus(TravelStatus.NO_ANSWER)
        .build();
    diagnosisRepo.upsertAsync(mutated).get();

    // Read it back again and verify.
    DiagnosisEntity readAgain = diagnosisRepo.getById(newId).get();
    assertThat(readAgain).isEqualTo(mutated);
  }

  @Test
  public void getAll_shouldReturnAllEntities() {
    diagnosisRepo.upsertAsync(DiagnosisEntity.newBuilder().setVerificationCode("code1").build());
    diagnosisRepo.upsertAsync(DiagnosisEntity.newBuilder().setVerificationCode("code2").build());
    diagnosisRepo.upsertAsync(DiagnosisEntity.newBuilder().setVerificationCode("code3").build());
    List<DiagnosisEntity> observer = new ArrayList<>();
    diagnosisRepo.getAllLiveData().observeForever(observer::addAll);

    assertThat(observer).hasSize(3);
    List<String> verificationCodes = observer.stream()
        .map(DiagnosisEntity::getVerificationCode)
        .collect(Collectors.toList());
    assertThat(verificationCodes).containsExactly("code1", "code2", "code3");
  }

  @Test
  public void getByVerificationCodeAsync_noneMatching_returnsEmpty()
      throws ExecutionException, InterruptedException {
    diagnosisRepo.upsertAsync(DiagnosisEntity.newBuilder().setId(1).setVerificationCode("code1").build());
    diagnosisRepo.upsertAsync(DiagnosisEntity.newBuilder().setId(2).setVerificationCode("code2").build());
    diagnosisRepo.upsertAsync(DiagnosisEntity.newBuilder().setId(3).setVerificationCode("code3").build());

    ListenableFuture<List<DiagnosisEntity>> entities = diagnosisRepo
        .getByVerificationCodeAsync("code4");

    assertThat(entities.get()).isEmpty();
  }

  @Test
  public void getByVerificationCodeAsync_singleMatch_returnsMatch()
      throws ExecutionException, InterruptedException {
    String matchingCode = "matchingCode";
    diagnosisRepo
        .upsertAsync(DiagnosisEntity.newBuilder().setId(1).setVerificationCode(matchingCode).build());
    diagnosisRepo.upsertAsync(DiagnosisEntity.newBuilder().setId(2).setVerificationCode("code2").build());
    diagnosisRepo.upsertAsync(DiagnosisEntity.newBuilder().setId(3).setVerificationCode("code3").build());

    ListenableFuture<List<DiagnosisEntity>> entities = diagnosisRepo
        .getByVerificationCodeAsync(matchingCode);

    assertThat(entities.get()).hasSize(1);
    List<Long> verificationCodes = entities.get().stream()
        .map(DiagnosisEntity::getId)
        .collect(Collectors.toList());
    assertThat(verificationCodes).containsExactly(1L);
  }

  @Test
  public void getByVerificationCodeAsync_multiMatch_returnsMatches()
      throws ExecutionException, InterruptedException {
    String matchingCode = "matchingCode";
    diagnosisRepo
        .upsertAsync(DiagnosisEntity.newBuilder().setId(1).setVerificationCode(matchingCode).build());
    diagnosisRepo
        .upsertAsync(DiagnosisEntity.newBuilder().setId(2).setVerificationCode(matchingCode).build());
    diagnosisRepo.upsertAsync(DiagnosisEntity.newBuilder().setId(3).setVerificationCode("code3").build());

    ListenableFuture<List<DiagnosisEntity>> entities = diagnosisRepo
        .getByVerificationCodeAsync(matchingCode);

    assertThat(entities.get()).hasSize(2);
    List<Long> verificationCodes = entities.get().stream()
        .map(DiagnosisEntity::getId)
        .collect(Collectors.toList());
    assertThat(verificationCodes).containsExactly(1L, 2L);
  }

  @Test
  public void deleteById_shouldDeleteRecord() throws Exception {
    // Create a record
    long newId = diagnosisRepo.upsertAsync(
        DiagnosisEntity.newBuilder().setVerificationCode("code1").build()).get();

    // Only to delete it
    diagnosisRepo.deleteByIdAsync(newId).get();

    // Reading back should return null.
    assertThat(diagnosisRepo.getById(newId).get()).isNull();
  }

  @Test
  public void getRevisionToken_noTokensStored_shouldReturnNull() throws Exception {
    diagnosisRepo.upsertAsync(DiagnosisEntity.newBuilder().setVerificationCode("code1").build());

    assertThat(diagnosisRepo.getMostRecentRevisionTokenAsync().get()).isNull();
  }

  @Test
  public void getRevisionToken_shouldReturnTheMostRecentNonEmptyToken() throws Exception {
    DiagnosisEntity diagnosis1 = DiagnosisEntity.newBuilder()
        .setVerificationCode("code1")
        .setCreatedTimestampMs(10L)
        .setRevisionToken("revisionToken1")
        .build();
    diagnosisRepo.upsertAsync(diagnosis1);

    DiagnosisEntity diagnosis2 = DiagnosisEntity.newBuilder()
        .setVerificationCode("code2")
        .setCreatedTimestampMs(42L)
        .setRevisionToken("revisionToken2")
        .build();
    diagnosisRepo.upsertAsync(diagnosis2);

    DiagnosisEntity diagnosis3 = DiagnosisEntity.newBuilder()
        .setVerificationCode("code3")
        .setCreatedTimestampMs(43L)
        .setRevisionToken("revisionToken3")
        .build();
    diagnosisRepo.upsertAsync(diagnosis3);

    DiagnosisEntity diagnosis4 = DiagnosisEntity.newBuilder()
        .setVerificationCode("code4")
        .setCreatedTimestampMs(44L)
        // No revision token.
        .build();
    diagnosisRepo.upsertAsync(diagnosis4);

    assertThat(diagnosisRepo.getMostRecentRevisionTokenAsync().get()).isEqualTo("revisionToken3");
  }

  @Test
  public void createOrMutateById_withNonexistentId_shouldCreateDiagnosis() throws Exception {
    // WHEN
    long newId = diagnosisRepo.createOrMutateById(
        -1, diagnosis -> diagnosis.toBuilder().setVerificationCode("code123").build());

    // THEN
    DiagnosisEntity readBack = diagnosisRepo.getById(newId).get();
    assertThat(readBack.getVerificationCode()).isEqualTo("code123");
  }

  @Test
  public void createOrMutateById_withExistingId_shouldMutateDiagnosis() throws Exception {
    // GIVEN
    long newId = diagnosisRepo.upsertAsync(
        DiagnosisEntity.newBuilder().setVerificationCode("code123").setCertificate("cert").build())
        .get();

    // WHEN
    diagnosisRepo.createOrMutateById(
        newId, diagnosis -> diagnosis.toBuilder().setVerificationCode("code456").build());

    // THEN
    DiagnosisEntity readBack = diagnosisRepo.getById(newId).get();
    // Make sure the one field is mutated
    assertThat(readBack.getVerificationCode()).isEqualTo("code456");
    // But not the other.
    assertThat(readBack.getCertificate()).isEqualTo("cert");
  }

  /**
   * Check that two {@link DiagnosisEntity} objects are the same, ignoring the {@code id} field.
   */
  private static void assertEqualIgnoringId(
      DiagnosisEntity result, DiagnosisEntity expected) {
    assertThat(result.toBuilder().setId(0).build())
        .isEqualTo(expected.toBuilder().setId(0).build());
  }
}