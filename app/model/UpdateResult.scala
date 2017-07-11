package model

abstract class UpdateResult[+T] {
  def successes: Seq[T]
  def failures: Seq[T]
}

object UpdateResult {
  def apply[T](results: Seq[Either[T, T]]): UpdateResult[T] = {

    results.foldLeft(List.empty[T], List.empty[T]){ case (acc, res) =>
      res match {
        case Left(l) => (acc._1 :+ l, acc._2)
        case Right(r) => (acc._1, acc._2 :+ r)
      }
    } match {
      case (f, Nil) => FailedUpdateResult(f)
      case (Nil, s) => SuccessfulUpdateResult(s)
      case (f, s) => PartialUpdateResult(f, s)
    }
  }

}

final case class SuccessfulUpdateResult[T](o: Seq[T]) extends UpdateResult[T] {
  def successes: Seq[T] = o
  def failures: Seq[T] = Seq.empty[T]

}

final case class FailedUpdateResult[T](o: Seq[T]) extends UpdateResult[T] {
  def successes = Seq.empty[T]
  def failures: Seq[T] = o
}

final case class PartialUpdateResult[T](failures: Seq[T], successes: Seq[T]) extends UpdateResult[T]
