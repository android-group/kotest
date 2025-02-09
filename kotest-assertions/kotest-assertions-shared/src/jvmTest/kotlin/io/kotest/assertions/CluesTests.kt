package io.kotest.assertions

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CluesTests : FunSpec({
   test("withClue should not fail on coroutine thread switch") {
      withContext(Dispatchers.Unconfined) {
         val threadIds = mutableSetOf<Long>()
         withClue("should not fail") {
            threadIds.add(Thread.currentThread().id)
            delay(10)
            threadIds.add(Thread.currentThread().id)
         }
         threadIds shouldHaveSize 2
      }
   }

   test("concurrent withClue invocations should be isolated from each other") {
      @OptIn(ExperimentalCoroutinesApi::class)
      withContext(Dispatchers.IO.limitedParallelism(8)) {
         val repetitionCount = 100
         val parentCoroutineCount = 2
         val childCoroutineCount = 10
         val stepCount = 2

         // These parameters specify a certain quota of repetitions to succeed in dispatching coroutines to
         // a specified minimum number of threads. This safeguards against subtle differences in dispatcher
         // implementations (how many threads to allocate, how many threads to actually use for coroutines).
         val minimumThreadCount = 2
         val minimumThreadRepetitionQuota = repetitionCount / 2

         var minimumThreadRepetitionCount = 0

         for (repetitionNumber in 1..repetitionCount) {
            val threadIds = mutableSetOf<Long>()

            coroutineScope {
               for (parentCoroutineNumber in 1..parentCoroutineCount) {
                  launch {
                     val parentClue = "r=$repetitionNumber, p=$parentCoroutineNumber"
                     withClue(parentClue) {
                        for (childCoroutineNumber in 1..childCoroutineCount) {
                           launch {
                              for (stepNumber in 1..stepCount) {
                                 val childClue = "r=$repetitionNumber, c=$childCoroutineNumber, s=$stepNumber"
                                 withClue(childClue) {
                                    clueContextAsString() shouldBe "$parentClue\n$childClue\n"
                                    synchronized(threadIds) { threadIds.add(Thread.currentThread().id) }
                                    delay(1L)
                                    clueContextAsString() shouldBe "$parentClue\n$childClue\n"
                                    synchronized(threadIds) { threadIds.add(Thread.currentThread().id) }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }

            if (threadIds.size >= minimumThreadCount)
               minimumThreadRepetitionCount++
         }

         minimumThreadRepetitionCount shouldBeGreaterThanOrEqual minimumThreadRepetitionQuota
      }
   }
})
