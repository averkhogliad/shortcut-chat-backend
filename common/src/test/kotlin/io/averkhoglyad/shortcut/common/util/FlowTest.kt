package io.averkhoglyad.shortcut.common.util

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.ints.shouldBeZero
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.take
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit.MILLISECONDS

@OptIn(ExperimentalCoroutinesApi::class)
class FlowTest : FreeSpec({

    "tickerFlow" - {
        "with initial delay" - {
            "should not emit before initial delay" {
                runTest {
                    val initialDelay = 500.milliseconds
                    val period = 1000.milliseconds
                    var ticks = 0

                    launch {
                        tickerFlow(period, initialDelay)
                            .take(1)
                            .collect { ticks++ }
                    }

                    advanceTimeBy(initialDelay - 1.milliseconds)
                    ticks.shouldBeZero()
                }
            }

            "should emit first value after initial delay" {
                runTest {
                    val initialDelay = 500.milliseconds
                    val period = 1000.milliseconds
                    var ticks = 0

                    launch {
                        tickerFlow(period, initialDelay)
                            .take(1)
                            .collect { ticks++ }
                    }

                    advanceTimeBy(initialDelay + 1.milliseconds)
                    ticks shouldBe 1
                }
            }
        }

        "with regular period" - {
            "should emit values at correct intervals" {
                runTest {
                    val period = 200.milliseconds
                    var ticks = 0;

                    launch {
                        tickerFlow(period)
                            .take(3)
                            .collect { ticks++ }
                    }

                    // first tick
                    advanceTimeBy(1.milliseconds)
                    ticks shouldBe 1

                    // second tick after first period
                    advanceTimeBy(period)
                    ticks shouldBe 2

                    // third tick after next period
                    advanceTimeBy(period)
                    ticks shouldBe 3
                }
            }
        }

        "when cancelled" - {
            "should stop emitting values" {
                runTest {
                    val period = 100.milliseconds
                    var ticks = 0

                    val job = launch {
                        tickerFlow(period).collect { ticks++ }
                    }

                    advanceTimeBy(period * 3)
                    ticks shouldBe 3

                    job.cancelAndJoin()

                    advanceTimeBy(period * 3)
                    ticks shouldBe 3
                }
            }
        }
    }

    "windowed" - {
        "emits empty list on empty flow" {
            runTest {
                // given
                val flow = emptyFlow<Int>()
                val windowSize = 100.milliseconds

                // when
                val result = flow.windowed(windowSize)
                    .toList()

                // then
                result shouldBe emptyList()
            }
        }

        "emits partial window when flow completes" {
            runTest {
                // given
                val events = listOf(1, 2)
                val windowSize = 100.milliseconds
                val flow = flow {
                    events.forEach {
                        emit(it)
                        delay(20)
                    }
                }

                // when
                val result = flow.windowed(windowSize)
                    .toList()

                // then
                result shouldHaveSingleElement events
            }
        }

        "handle single element per window" {
            runTest {
                // given
                val events = listOf(1, 2, 3, 4, 5)
                val windowSize = 100.milliseconds
                val flow = flow {
                    delay(windowSize / 2)
                    events.forEach {
                        emit(it)
                        delay(windowSize)
                    }
                }

                // when
                val result = flow.windowed(windowSize)
                    .toList()

                // then
                result shouldContainExactly events.map { listOf(it) }
            }
        }

        "handle element 1ms before window finished" {
            runTest {
                // given
                val events = listOf(1, 2)
                val windowSize = 100.milliseconds
                val flow = flow {
                    events.forEach {
                        emit(it)
                        delay(windowSize - 1.milliseconds)
                    }
                }

                // when
                val result = flow.windowed(windowSize)
                    .toList()

                // then
                result shouldHaveSingleElement events
            }
        }

        "handle element 1ms after window finished" {
            runTest {
                // given
                val events = listOf(1, 2)
                val windowSize = 100.milliseconds
                val flow = flow {
                    events.forEach {
                        emit(it)
                        delay(windowSize + 1.milliseconds)
                    }
                }

                // when
                val result = flow.windowed(windowSize)
                    .toList()

                // then
                result shouldContainExactly events.map { listOf(it) }
            }
        }

        "handle element in border of windows and put in one of the windows once only" {
            runTest {
                // given
                val events = listOf(1, 2)
                val windowSize = 100.milliseconds
                val flow = flow {
                    events.forEach {
                        emit(it)
                        delay(windowSize)
                    }
                }

                // when
                val result = flow.windowed(windowSize)
                    .toList()

                // then
                result.size shouldBeInRange 1..2
                result.flatMap { it } shouldContainExactly events
            }
        }

        "handles rapid emissions within single window" {
            runTest {
                // given
                val windowSize = 100.milliseconds
                val events = Arb.int()
                    .take(windowSize.toInt(MILLISECONDS))
                    .toList()

                val flow = flow {
                    events.forEach {
                        emit(it)
                        delay(1.milliseconds)
                    }
                }

                // when
                val result = flow.windowed(windowSize)
                    .toList()

                // then
                result shouldHaveSingleElement events
            }
        }

        "handles rapid emissions within multiple windows" {
            runTest {
                // given
                val windowSize = 100.milliseconds
                val windowsCount = 10
                val events = Arb.int()
                    .take(windowSize.toInt(MILLISECONDS) * windowsCount)
                    .toList()

                val flow = flow {
                    events.forEach {
                        emit(it)
                        delay(1.milliseconds)
                    }
                }

                // when
                val result = flow.windowed(windowSize)
                    .toList()

                // then
                result shouldHaveSize windowsCount
                result.flatMap { it } shouldContainExactly events
            }
        }
    }
})
