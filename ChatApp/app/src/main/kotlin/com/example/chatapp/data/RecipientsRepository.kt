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
package com.example.chatapp.data

import com.example.chatapp.appfunctions.AppFunctions.ChatGroup
import com.example.chatapp.appfunctions.AppFunctions.ContactSearchResult
import com.example.chatapp.appfunctions.AppFunctions.Recipient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository responsible for managing and providing access to contact information.
 *
 * This class serves as a data source for [Recipient] objects, currently maintaining a static list
 * of contacts and providing functionality to filter them via search queries.
 */
@Singleton
class RecipientsRepository
    @Inject
    constructor() {
        private val recipients =
            listOf(
                Recipient(id = "1", name = "Alice Smith", email = "alice@example.com"),
                Recipient(id = "2", name = "Bob Johnson", email = "bob@example.com"),
                Recipient(id = "3", name = "Charlie Brown", email = "charlie@example.com"),
                Recipient(id = "4", name = "David Wilson", email = "david@example.com"),
                Recipient(id = "5", name = "Eve Davis", email = "eve@example.com"),
                Recipient(id = "6", name = "Frank Miller", email = "frank@example.com"),
            )

        private val groups =
            listOf(
                ChatGroup(
                    id = "g1",
                    name = "Work Friends",
                    recipients = recipients.take(3),
                ),
                ChatGroup(
                    id = "g2",
                    name = "Family",
                    recipients = recipients.takeLast(2),
                ),
            )

        /**
         * Retrieves all available contacts.
         *
         * @return A list of all [Recipient] objects.
         */
        fun getAllRecipients(): List<Recipient> {
            return recipients
        }

        /**
         * Retrieves all available groups.
         *
         * @return A list of all [ChatGroup] objects.
         */
        fun getAllGroups(): List<ChatGroup> {
            return groups
        }

        /**
         * Searches for contacts that match the given query.
         *
         * The search is case-insensitive and matches against both the contact's name and email address.
         * If the [query] is blank, return [maxCount] contacts.
         *
         * @param query The search term used to filter contacts.
         * @param maxCount Maximum recipients to return if [query] is blank.
         * @return A list of [Recipient] objects that match the search criteria.
         */
        fun searchRecipients(
            query: String?,
            maxCount: Int,
        ): List<Recipient> {
            if (query.isNullOrBlank()) {
                // TODO:Return most recently contacted.
                return recipients.take(maxCount)
            }

            return recipients.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.email.contains(
                        query,
                        ignoreCase = true,
                    )
            }
        }

        /**
         * Searches for groups that match the given query.
         *
         * The search is case-insensitive and matches against the group's name.
         * If the [query] is blank, return [maxCount] groups.
         *
         * @param query The search term used to filter groups.
         * @param maxCount Maximum groups to return if [query] is blank.
         * @return A list of [ChatGroup] objects that match the search criteria.
         */
        fun searchGroups(
            query: String?,
            maxCount: Int,
        ): List<ChatGroup> {
            if (query.isNullOrBlank()) {
                return groups.take(maxCount)
            }

            return groups.filter {
                it.name.contains(query, ignoreCase = true)
            }
        }

        /**
         * Searches for any entity (contact or group) matching the query.
         *
         * @param query Search string for name.
         * @param maxCount Maximum number of results to return per entity type.
         * @return A unified list of [ContactSearchResult] containing both individuals and groups.
         */
        fun searchAny(
            query: String?,
            maxCount: Int,
        ): List<ContactSearchResult> {
            val individuals =
                searchRecipients(query, maxCount).map {
                    ContactSearchResult(
                        endpointValue = it.id,
                        endpointType = "INDIVIDUAL",
                        displayName = it.name,
                    )
                }
            val groups =
                searchGroups(query, maxCount).map {
                    ContactSearchResult(
                        endpointValue = it.id,
                        endpointType = "GROUP",
                        displayName = it.name,
                    )
                }
            return mutableListOf<ContactSearchResult>().apply {
                addAll(individuals)
                addAll(groups)
            }
        }

        fun getRecipientById(id: String): Recipient? = recipients.singleOrNull { it.id == id }

        fun getRecipientByName(name: String): Recipient? = recipients.singleOrNull { it.name == name }

        fun getGroupById(id: String): ChatGroup? = groups.singleOrNull { it.id == id }
    }
