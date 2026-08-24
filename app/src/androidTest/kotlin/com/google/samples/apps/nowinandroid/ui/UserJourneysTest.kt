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

package com.google.samples.apps.nowinandroid.ui

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import com.google.samples.apps.nowinandroid.MainActivity
import com.google.samples.apps.nowinandroid.core.data.repository.NewsRepository
import com.google.samples.apps.nowinandroid.core.data.repository.TopicsRepository
import com.google.samples.apps.nowinandroid.core.model.data.NewsResource
import com.google.samples.apps.nowinandroid.core.model.data.Topic
import com.google.samples.apps.nowinandroid.core.rules.GrantPostNotificationsPermissionRule
import com.google.samples.apps.nowinandroid.feature.interests.impl.LIST_PANE_TEST_TAG
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import com.google.samples.apps.nowinandroid.core.ui.R as CoreUiR
import com.google.samples.apps.nowinandroid.feature.bookmarks.api.R as BookmarksR
import com.google.samples.apps.nowinandroid.feature.foryou.api.R as FeatureForyouR
import com.google.samples.apps.nowinandroid.feature.search.api.R as FeatureSearchR
import com.google.samples.apps.nowinandroid.feature.settings.impl.R as SettingsR

/**
 * Tests the user journeys that span several top level destinations, complementing the
 * per screen tests in the feature modules and the routing tests in [NavigationTest].
 */
@HiltAndroidTest
class UserJourneysTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val postNotificationsPermission = GrantPostNotificationsPermissionRule()

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var topicsRepository: TopicsRepository

    @Inject
    lateinit var newsRepository: NewsRepository

    private val forYou by composeTestRule.stringResource(FeatureForyouR.string.feature_foryou_api_title)
    private val saved by composeTestRule.stringResource(BookmarksR.string.feature_bookmarks_api_title)
    private val interests by composeTestRule.stringResource(FeatureSearchR.string.feature_search_api_interests)
    private val emptySavedTitle by composeTestRule.stringResource(BookmarksR.string.feature_bookmarks_api_empty_error)
    private val bookmark by composeTestRule.stringResource(CoreUiR.string.core_ui_bookmark)
    private val unbookmark by composeTestRule.stringResource(CoreUiR.string.core_ui_unbookmark)
    private val follow by composeTestRule.stringResource(CoreUiR.string.core_ui_interests_card_follow_button_content_desc)
    private val unfollow by composeTestRule.stringResource(CoreUiR.string.core_ui_interests_card_unfollow_button_content_desc)
    private val search by composeTestRule.stringResource(SettingsR.string.feature_settings_impl_top_app_bar_navigation_icon_description)
    private val undo by composeTestRule.stringResource(BookmarksR.string.feature_bookmarks_api_undo)
    private val settings by composeTestRule.stringResource(SettingsR.string.feature_settings_impl_top_app_bar_action_icon_description)
    private val darkMode by composeTestRule.stringResource(SettingsR.string.feature_settings_impl_dark_mode_config_dark)
    private val dismissSettings by composeTestRule.stringResource(SettingsR.string.feature_settings_impl_dismiss_dialog_button_text)

    @Before
    fun setup() = hiltRule.inject()

    @Test
    fun bookmarkingNewsResourceFromForYou_showsItOnSaved() {
        val newsResource = firstNewsResource()

        composeTestRule.apply {
            followTopicFromOnboarding(newsResource.topics.first())
            scrollForYouFeedTo(newsResource)

            bookmarkButtonOf(newsResource, contentDescription = bookmark).performClick()

            onNodeWithText(saved).performClick()
            onNodeWithText(newsResource.title, substring = true).assertExists()
        }
    }

    @Test
    fun removingTheOnlyBookmarkFromSaved_showsEmptyState() {
        val newsResource = firstNewsResource()

        composeTestRule.apply {
            followTopicFromOnboarding(newsResource.topics.first())
            scrollForYouFeedTo(newsResource)
            bookmarkButtonOf(newsResource, contentDescription = bookmark).performClick()

            onNodeWithText(saved).performClick()
            bookmarkButtonOf(newsResource, contentDescription = unbookmark).performClick()

            onNodeWithText(emptySavedTitle).assertExists()
        }
    }

    @Test
    fun undoingBookmarkRemoval_restoresTheBookmark() {
        val newsResource = firstNewsResource()

        composeTestRule.apply {
            followTopicFromOnboarding(newsResource.topics.first())
            scrollForYouFeedTo(newsResource)
            bookmarkButtonOf(newsResource, contentDescription = bookmark).performClick()

            onNodeWithText(saved).performClick()
            bookmarkButtonOf(newsResource, contentDescription = unbookmark).performClick()

            onNodeWithText(undo).performClick()
            onNodeWithText(newsResource.title, substring = true).assertExists()
        }
    }

    @Test
    fun changingDarkModePreference_isKeptWhenTheSettingsDialogIsReopened() {
        composeTestRule.apply {
            onNodeWithContentDescription(settings).performClick()
            onNodeWithText(darkMode).performClick()
            onNodeWithText(dismissSettings).performClick()

            onNodeWithContentDescription(settings).performClick()
            onNodeWithText(darkMode).assertIsSelected()
        }
    }

    @Test
    fun openingTopicFromInterests_showsTopicDetails() {
        val topic = firstTopicByName()

        composeTestRule.apply {
            onNodeWithText(interests).performClick()
            onNodeWithTag(LIST_PANE_TEST_TAG).performScrollToNode(hasText(topic.name))
            onNodeWithText(topic.name).performClick()

            onNodeWithTag("topic:${topic.id}").assertExists()
        }
    }

    @Test
    fun followingTopicFromInterests_showsTopicAsFollowedOnForYou() {
        val topic = firstTopicByName()

        composeTestRule.apply {
            onNodeWithText(interests).performClick()
            onNodeWithTag(LIST_PANE_TEST_TAG).performScrollToNode(hasText(topic.name))

            onAllNodesWithContentDescription(follow)
                .filter(hasAnyAncestor(hasText(topic.name)))
                .onFirst()
                .performClick()

            onAllNodesWithContentDescription(unfollow)
                .filter(hasAnyAncestor(hasText(topic.name)))
                .onFirst()
                .assertExists()

            onNodeWithText(forYou).performClick()
            onNodeWithTag("forYou:topicSelection")
                .performScrollToNode(hasContentDescription(topic.name))
            onNodeWithContentDescription(topic.name).assertIsOn()
        }
    }

    @Test
    fun searchFromTopAppBar_opensSearchAndBackReturnsToForYou() {
        composeTestRule.apply {
            onNodeWithContentDescription(search).performClick()

            onNodeWithTag("searchTextField")
                .assertIsFocused()
                .performTextInput("compose")

            Espresso.pressBack()

            onNodeWithText(forYou).assertExists()
        }
    }

    private fun firstNewsResource(): NewsResource = runBlocking {
        newsRepository.getNewsResources().first().first()
    }

    private fun firstTopicByName(): Topic = runBlocking {
        topicsRepository.getTopics().first().sortedBy(Topic::name).first()
    }

    private fun followTopicFromOnboarding(topic: Topic) =
        composeTestRule.onNodeWithText(topic.name).performClick()

    private fun scrollForYouFeedTo(newsResource: NewsResource) =
        composeTestRule.onNodeWithTag("forYou:feed")
            .performScrollToNode(hasTestTag("newsResourceCard:${newsResource.id}"))

    private fun bookmarkButtonOf(newsResource: NewsResource, contentDescription: String) =
        composeTestRule.onAllNodesWithContentDescription(contentDescription)
            .filter(hasAnyAncestor(hasText(newsResource.title, substring = true)))
            .onFirst()
}
