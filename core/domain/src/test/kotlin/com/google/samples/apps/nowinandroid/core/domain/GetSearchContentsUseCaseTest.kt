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

import com.google.samples.apps.nowinandroid.core.testing.data.newsResourcesTestData
import com.google.samples.apps.nowinandroid.core.testing.data.topicsTestData
import com.google.samples.apps.nowinandroid.core.testing.repository.TestSearchContentsRepository
import com.google.samples.apps.nowinandroid.core.testing.repository.TestUserDataRepository
import com.google.samples.apps.nowinandroid.core.testing.repository.emptyUserData
import com.google.samples.apps.nowinandroid.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GetSearchContentsUseCaseTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val searchContentsRepository = TestSearchContentsRepository()
    private val userDataRepository = TestUserDataRepository()

    private val useCase = GetSearchContentsUseCase(
        searchContentsRepository = searchContentsRepository,
        userDataRepository = userDataRepository,
    )

    @Before
    fun setup() {
        userDataRepository.setUserData(emptyUserData)
        searchContentsRepository.addTopics(topicsTestData)
        searchContentsRepository.addNewsResources(newsResourcesTestData)
    }

    @Test
    fun onlyContentMatchingTheQueryIsReturned() = runTest {
        val result = useCase("Headlines").first()

        assertEquals(
            listOf(topicsTestData[0].id),
            result.topics.map { it.topic.id },
        )
        assertTrue(result.newsResources.isEmpty())
    }

    @Test
    fun matchingTopicsAreMarkedAsFollowed() = runTest {
        userDataRepository.setFollowedTopicIds(setOf(topicsTestData[1].id))

        val result = useCase("Material Design").first()

        val followableTopic = result.topics.single()
        assertEquals(topicsTestData[1].id, followableTopic.topic.id)
        assertTrue(followableTopic.isFollowed)
    }

    @Test
    fun matchingNewsResourcesCarryTheirBookmarkedAndViewedState() = runTest {
        val bookmarked = newsResourcesTestData[0]
        userDataRepository.setNewsResourceBookmarked(bookmarked.id, true)
        userDataRepository.setNewsResourceViewed(bookmarked.id, true)

        val result = useCase("Android Basics with Compose").first()

        val userNewsResource = result.newsResources.single { it.id == bookmarked.id }
        assertTrue(userNewsResource.isSaved)
        assertTrue(userNewsResource.hasBeenViewed)
    }

    @Test
    fun newsResourcesAreNotSavedOrViewedByDefault() = runTest {
        val result = useCase("Android Basics with Compose").first()

        val userNewsResource = result.newsResources.single()
        assertFalse(userNewsResource.isSaved)
        assertFalse(userNewsResource.hasBeenViewed)
    }
}
