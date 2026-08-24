/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.samples.apps.nowinandroid.core.domain

import com.google.samples.apps.nowinandroid.core.testing.repository.TestRecentSearchRepository
import com.google.samples.apps.nowinandroid.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetRecentSearchQueriesUseCaseTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val recentSearchRepository = TestRecentSearchRepository()

    private val useCase = GetRecentSearchQueriesUseCase(recentSearchRepository)

    @Test
    fun recentSearchesAreReturnedMostRecentFirst() = runTest {
        recentSearchRepository.insertOrReplaceRecentSearch("compose")
        recentSearchRepository.insertOrReplaceRecentSearch("kotlin")

        assertEquals(
            listOf("kotlin", "compose"),
            useCase().first().map { it.query },
        )
    }

    @Test
    fun theNumberOfRecentSearchesIsCappedByTheLimit() = runTest {
        repeat(times = 3) { index ->
            recentSearchRepository.insertOrReplaceRecentSearch("query $index")
        }

        assertEquals(
            listOf("query 2"),
            useCase(limit = 1).first().map { it.query },
        )
    }

    @Test
    fun noRecentSearchesAreReturnedAfterClearing() = runTest {
        recentSearchRepository.insertOrReplaceRecentSearch("compose")
        recentSearchRepository.clearRecentSearches()

        assertTrue(useCase().first().isEmpty())
    }
}
