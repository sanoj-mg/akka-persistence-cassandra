package akka.persistence.cassandra.journal

import akka.persistence.cassandra.journal.StreamMerger._
import org.scalatest.{MustMatchers, WordSpecLike}

class StreamMergerTest extends WordSpecLike with MustMatchers {

  "Stream merger component" must {
    "merge single stream" in {
      val entries = Seq(
        JournalEntry(JournalId("1"), 0, PersistenceId("1"), 1, null),
        JournalEntry(JournalId("1"), 1, PersistenceId("1"), 2, null))

      val stream = Stream(JournalId("1"), entries.iterator)

      val mergeResult =
        StreamMerger.merge(Map[JournalId, Long](), Map[PersistenceId, Long](), Seq(stream))

      mergeResult must be(MergeSuccess(
          Map[JournalId, Long](JournalId("1") -> 1),
          Map[PersistenceId, Long](PersistenceId("1") -> 2),
          entries))
    }

    "merge reordered stream" in {
      val entries = Seq(
        JournalEntry(JournalId("1"), 0, PersistenceId("1"), 1, null),
        JournalEntry(JournalId("1"), 1, PersistenceId("1"), 2, null),
        JournalEntry(JournalId("1"), 2, PersistenceId("1"), 4, null),
        JournalEntry(JournalId("1"), 3, PersistenceId("1"), 3, null))

      val correctEntries = Seq(
        JournalEntry(JournalId("1"), 0, PersistenceId("1"), 1, null),
        JournalEntry(JournalId("1"), 1, PersistenceId("1"), 2, null),
        JournalEntry(JournalId("1"), 3, PersistenceId("1"), 3, null),
        JournalEntry(JournalId("1"), 2, PersistenceId("1"), 4, null))

      val stream = Stream(JournalId("1"), entries.iterator)

      val mergeResult =
        StreamMerger.merge(Map[JournalId, Long](), Map[PersistenceId, Long](), Seq(stream))

      mergeResult must be(MergeSuccess(
        Map[JournalId, Long](JournalId("1") -> 3),
        Map[PersistenceId, Long](PersistenceId("1") -> 4),
        correctEntries))
    }

    "merge stream not from beginning" in {
      val entries = Seq(
        JournalEntry(JournalId("1"), 7, PersistenceId("1"), 4, null),
        JournalEntry(JournalId("1"), 8, PersistenceId("1"), 5, null))

      val correctEntries = Seq(
        JournalEntry(JournalId("1"), 7, PersistenceId("1"), 4, null),
        JournalEntry(JournalId("1"), 8, PersistenceId("1"), 5, null))

      val stream = Stream(JournalId("1"), entries.iterator)

      val mergeResult =
        StreamMerger.merge(
          Map[JournalId, Long](JournalId("1") -> 6),
          Map[PersistenceId, Long](PersistenceId("1") -> 3),
          Seq(stream))

      mergeResult must be(MergeSuccess(
        Map[JournalId, Long](JournalId("1") -> 8),
        Map[PersistenceId, Long](PersistenceId("1") -> 5),
        correctEntries))
    }

    "merge stream with multiple persistenceIds" in {
      val entries = Seq(
        JournalEntry(JournalId("1"), 0, PersistenceId("1"), 1, null),
        JournalEntry(JournalId("1"), 1, PersistenceId("1"), 2, null),
        JournalEntry(JournalId("1"), 2, PersistenceId("2"), 1, null))

      val stream = Stream(JournalId("1"), entries.iterator)

      val mergeResult =
        StreamMerger.merge(Map[JournalId, Long](), Map[PersistenceId, Long](), Seq(stream))

      mergeResult must be(MergeSuccess(
        Map[JournalId, Long](JournalId("1") -> 2),
        Map[PersistenceId, Long](PersistenceId("1") -> 2, PersistenceId("2") -> 1),
        entries))
    }

    "merge multiple streams" in {
      val entries1 = Seq(
        JournalEntry(JournalId("1"), 0, PersistenceId("1"), 1, null),
        JournalEntry(JournalId("1"), 1, PersistenceId("1"), 2, null))

      val entries2 = Seq(
        JournalEntry(JournalId("2"), 0, PersistenceId("2"), 1, null),
        JournalEntry(JournalId("2"), 1, PersistenceId("2"), 2, null),
        JournalEntry(JournalId("2"), 2, PersistenceId("2"), 3, null))

      val correctEntries = Seq(
        JournalEntry(JournalId("1"), 0, PersistenceId("1"), 1, null),
        JournalEntry(JournalId("2"), 0, PersistenceId("2"), 1, null),
        JournalEntry(JournalId("1"), 1, PersistenceId("1"), 2, null),
        JournalEntry(JournalId("2"), 1, PersistenceId("2"), 2, null),
        JournalEntry(JournalId("2"), 2, PersistenceId("2"), 3, null)
      )

      val stream1 = Stream(JournalId("1"), entries1.iterator)
      val stream2 = Stream(JournalId("2"), entries2.iterator)

      val mergeResult =
        StreamMerger.merge(Map[JournalId, Long](), Map[PersistenceId, Long](), Seq(stream1, stream2))

      mergeResult must be(MergeSuccess(
        Map[JournalId, Long](JournalId("1") -> 1, JournalId("2") -> 2),
        Map[PersistenceId, Long](PersistenceId("1") -> 2, PersistenceId("2") -> 3),
        correctEntries))
    }

    "merge multiple streams waiting on each other" in {
      val entries1 = Seq(
        JournalEntry(JournalId("1"), 0, PersistenceId("1"), 1, null),
        JournalEntry(JournalId("1"), 1, PersistenceId("1"), 3, null),
        JournalEntry(JournalId("1"), 2, PersistenceId("2"), 2, null))

      val entries2 = Seq(
        JournalEntry(JournalId("2"), 0, PersistenceId("2"), 1, null),
        JournalEntry(JournalId("2"), 1, PersistenceId("2"), 3, null),
        JournalEntry(JournalId("2"), 2, PersistenceId("1"), 2, null))

      val correctEntries = Seq(
        JournalEntry(JournalId("1"), 0, PersistenceId("1"), 1, null),
        JournalEntry(JournalId("2"), 0, PersistenceId("2"), 1, null),
        JournalEntry(JournalId("1"), 2, PersistenceId("2"), 2, null),
        JournalEntry(JournalId("2"), 1, PersistenceId("2"), 3, null),
        JournalEntry(JournalId("2"), 2, PersistenceId("1"), 2, null),
        JournalEntry(JournalId("1"), 1, PersistenceId("1"), 3, null)
      )

      val stream1 = Stream(JournalId("1"), entries1.iterator)
      val stream2 = Stream(JournalId("2"), entries2.iterator)

      val mergeResult =
        StreamMerger.merge(Map[JournalId, Long](), Map[PersistenceId, Long](), Seq(stream1, stream2))

      mergeResult must be(MergeSuccess(
        Map[JournalId, Long](JournalId("1") -> 2, JournalId("2") -> 2),
        Map[PersistenceId, Long](PersistenceId("1") -> 3, PersistenceId("2") -> 3),
        correctEntries))
    }

    "not merge unresolvable stream" in {
      val entries = Seq(
        JournalEntry(JournalId("1"), 0, PersistenceId("1"), 1, null),
        JournalEntry(JournalId("1"), 1, PersistenceId("1"), 3, null),
        JournalEntry(JournalId("1"), 2, PersistenceId("1"), 4, null))

      val correctEntries = Seq(JournalEntry(JournalId("1"), 0, PersistenceId("1"), 1, null))
      val unmerged = Set(
        JournalEntry(JournalId("1"), 1, PersistenceId("1"), 3, null),
        JournalEntry(JournalId("1"), 2, PersistenceId("1"), 4, null))

      val stream = Stream(JournalId("2"), entries.iterator)

      val mergeResult =
        StreamMerger.merge(Map[JournalId, Long](), Map[PersistenceId, Long](), Seq(stream))

      mergeResult must be(MergeFailure(
        Map[JournalId, Long](JournalId("1") -> 0),
        Map[PersistenceId, Long](PersistenceId("1") -> 1),
        correctEntries,
        unmerged))
    }
  }
}