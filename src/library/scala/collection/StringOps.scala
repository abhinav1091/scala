package scala
package collection

import java.lang.{StringBuilder => JStringBuilder}
import java.util.NoSuchElementException

import scala.collection.immutable.{ArraySeq, WrappedString}
import scala.collection.mutable.StringBuilder
import scala.math.{ScalaNumber, max, min}
import scala.reflect.ClassTag
import scala.util.matching.Regex

object StringOps {
  // just statics for companion class.
  private final val LF = 0x0A
  private final val FF = 0x0C
  private final val CR = 0x0D
  private final val SU = 0x1A

  private class StringIterator(private[this] val s: String) extends AbstractIterator[Char] {
    private[this] var pos = 0
    def hasNext: Boolean = pos < s.length
    def next(): Char = try {
      val r = s.charAt(pos)
      pos += 1
      r
    } catch { case _: IndexOutOfBoundsException => Iterator.empty.next() }
  }

  private class ReverseIterator(private[this] val s: String) extends AbstractIterator[Char] {
    private[this] var pos = s.length-1
    def hasNext: Boolean = pos >= 0
    def next(): Char = try {
      val r = s.charAt(pos)
      pos -= 1
      r
    } catch { case _: IndexOutOfBoundsException => Iterator.empty.next() }
  }

  private class GroupedIterator(s: String, groupSize: Int) extends AbstractIterator[String] {
    private[this] var pos = 0
    def hasNext: Boolean = pos < s.length
    def next(): String = {
      if(pos >= s.length) Iterator.empty.next()
      val r = s.slice(pos, pos+groupSize)
      pos += groupSize
      r
    }
  }

  /** A lazy filtered string. No filtering is applied until one of `foreach`, `map` or `flatMap` is called. */
  class WithFilter(p: Char => Boolean, s: String) {

    /** Apply `f` to each element for its side effects.
      * Note: [U] parameter needed to help scalac's type inference.
      */
    def foreach[U](f: Char => U): Unit = {
      val len = s.length
      var i = 0
      while(i < len) {
        val x = s.charAt(i)
        if(p(x)) f(x)
        i += 1
      }
    }

    /** Builds a new collection by applying a function to all chars of this filtered String.
      *
      *  @param f      the function to apply to each char.
      *  @return       a new collection resulting from applying the given function
      *                `f` to each char of this String and collecting the results.
      */
    def map[B](f: Char => B): immutable.IndexedSeq[B] = {
      val len = s.length
      val b = immutable.IndexedSeq.newBuilder[B]
      b.sizeHint(len)
      var i = 0
      while (i < len) {
        val x = s.charAt(i)
        if(p(x)) b.addOne(f(x))
        i += 1
      }
      b.result()
    }

    /** Builds a new String by applying a function to all chars of this filtered String.
      *
      *  @param f      the function to apply to each char.
      *  @return       a new String resulting from applying the given function
      *                `f` to each char of this String and collecting the results.
      */
    def map(f: Char => Char): String = {
      val len = s.length
      val sb = new JStringBuilder(len)
      var i = 0
      while (i < len) {
        val x = s.charAt(i)
        if(p(x)) sb.append(x)
        i += 1
      }
      sb.toString
    }

    /** Builds a new collection by applying a function to all chars of this filtered String
      * and using the elements of the resulting collections.
      *
      *  @param f      the function to apply to each char.
      *  @return       a new collection resulting from applying the given collection-valued function
      *                `f` to each char of this String and concatenating the results.
      */
    def flatMap[B](f: Char => IterableOnce[B]): immutable.IndexedSeq[B] = {
      val len = s.length
      val b = immutable.IndexedSeq.newBuilder[B]
      var i = 0
      while (i < len) {
        val x = s.charAt(i)
        if(p(x)) b.addAll(f(x))
        i += 1
      }
      b.result()
    }

    /** Builds a new String by applying a function to all chars of this filtered String
      * and using the elements of the resulting Strings.
      *
      *  @param f      the function to apply to each char.
      *  @return       a new String resulting from applying the given string-valued function
      *                `f` to each char of this String and concatenating the results.
      */
    def flatMap(f: Char => String): String = {
      val len = s.length
      val sb = new JStringBuilder
      var i = 0
      while (i < len) {
        val x = s.charAt(i)
        if(p(x)) sb.append(f(x))
        i += 1
      }
      sb.toString
    }

    /** Creates a new non-strict filter which combines this filter with the given predicate. */
    def withFilter(q: Char => Boolean): WithFilter = new WithFilter(a => p(a) && q(a), s)
  }
}

final class StringOps(private val s: String) extends AnyVal {
  import StringOps._

  @`inline` def view: StringView = new StringView(s)

  @`inline` def size: Int = s.length

  @`inline` def knownSize: Int = s.length

  /** Get the char at the specified index. */
  @`inline` def apply(i: Int): Char = s.charAt(i)

  def lengthCompare(len: Int): Int = Integer.compare(s.length, len)

  /** Builds a new collection by applying a function to all chars of this String.
    *
    *  @param f      the function to apply to each char.
    *  @return       a new collection resulting from applying the given function
    *                `f` to each char of this String and collecting the results.
    */
  def map[B](f: Char => B): immutable.IndexedSeq[B] = {
    val len = s.length
    val dst = new Array[AnyRef](len)
    var i = 0
    while (i < len) {
      dst(i) = f(s charAt i).asInstanceOf[AnyRef]
      i += 1
    }
    new ArraySeq.ofRef(dst).asInstanceOf[immutable.IndexedSeq[B]]
  }

  /** Builds a new String by applying a function to all chars of this String.
    *
    *  @param f      the function to apply to each char.
    *  @return       a new String resulting from applying the given function
    *                `f` to each char of this String and collecting the results.
    */
  def map(f: Char => Char): String = {
    val len = s.length
    val dst = new Array[Char](len)
    var i = 0
    while (i < len) {
      dst(i) = f(s charAt i)
      i += 1
    }
    new String(dst)
  }

  /** Builds a new collection by applying a function to all chars of this String
    * and using the elements of the resulting collections.
    *
    *  @param f      the function to apply to each char.
    *  @return       a new collection resulting from applying the given collection-valued function
    *                `f` to each char of this String and concatenating the results.
    */
  def flatMap[B](f: Char => IterableOnce[B]): immutable.IndexedSeq[B] = {
    val len = s.length
    val b = immutable.IndexedSeq.newBuilder[B]
    var i = 0
    while (i < len) {
      b.addAll(f(s.charAt(i)))
      i += 1
    }
    b.result()
  }

  /** Builds a new String by applying a function to all chars of this String
    * and using the elements of the resulting Strings.
    *
    *  @param f      the function to apply to each char.
    *  @return       a new String resulting from applying the given string-valued function
    *                `f` to each char of this String and concatenating the results.
    */
  def flatMap(f: Char => String): String = {
    val len = s.length
    val sb = new JStringBuilder
    var i = 0
    while (i < len) {
      sb append f(s.charAt(i))
      i += 1
    }
    sb.toString
  }

  /** Returns a new collection containing the chars from this String followed by the elements from the
    * right hand operand.
    *
    *  @param suffix the collection to append.
    *  @return       a new collection which contains all chars
    *                of this String followed by all elements of `suffix`.
    */
  def concat[B >: Char](suffix: Iterable[B]): immutable.IndexedSeq[B] = {
    val b = immutable.IndexedSeq.newBuilder[B]
    val k = suffix.knownSize
    b.sizeHint(s.length + (if(k >= 0) k else 16))
    b.addAll(new WrappedString(s))
    b.addAll(suffix)
    b.result()
  }

  /** Returns a new String containing the chars from this String followed by the chars from the
    * right hand operand.
    *
    *  @param suffix the collection to append.
    *  @return       a new String which contains all chars
    *                of this String followed by all chars of `suffix`.
    */
  def concat(suffix: IterableOnce[Char]): String = {
    val k = suffix.knownSize
    val sb = new JStringBuilder(s.length + (if(k >= 0) k else 16))
    sb.append(s)
    for (ch <- suffix.iterator) sb.append(ch)
    sb.toString
  }

  /** Returns a new String containing the chars from this String followed by the chars from the
    * right hand operand.
    *
    *  @param suffix the String to append.
    *  @return       a new String which contains all chars
    *                of this String followed by all chars of `suffix`.
    */
  @`inline` def concat(suffix: String): String = s + suffix

  /** Alias for `concat` */
  @`inline` def ++[B >: Char](suffix: Iterable[B]): immutable.IndexedSeq[B] = concat(suffix)

  /** Alias for `concat` */
  @`inline` def ++(suffix: IterableOnce[Char]): String = concat(suffix)

  /** Alias for `concat` */
  def ++(xs: String): String = concat(xs)

  /** Returns a collection with an element appended until a given target length is reached.
    *
    *  @param  len   the target length
    *  @param  elem  the padding value
    *  @return a collection consisting of
    *          this String followed by the minimal number of occurrences of `elem` so
    *          that the resulting collection has a length of at least `len`.
    */
  def padTo[B >: Char](len: Int, elem: B): immutable.IndexedSeq[B]  = {
    val sLen = s.length
    if (sLen >= len) new WrappedString(s) else {
      val b = immutable.IndexedSeq.newBuilder[B]
      b.sizeHint(len)
      b.addAll(new WrappedString(s))
      var i = sLen
      while (i < len) {
        b.addOne(elem)
        i += 1
      }
      b.result()
    }
  }

  /** Returns a String with a char appended until a given target length is reached.
    *
    *  @param   len   the target length
    *  @param   elem  the padding value
    *  @return a String consisting of
    *          this String followed by the minimal number of occurrences of `elem` so
    *          that the resulting String has a length of at least `len`.
    */
  def padTo(len: Int, elem: Char): String = {
    val sLen = s.length
    if (sLen >= len) s else {
      val sb = new JStringBuilder(len)
      sb.append(s)
      // With JDK 11, this can written as:
      // sb.append(String.valueOf(elem).repeat(len - sLen))
      var i = sLen
      while (i < len) {
        sb.append(elem)
        i += 1
      }
      sb.toString
    }
  }

  /** A copy of the String with an element prepended */
  def prepended[B >: Char](elem: B): immutable.IndexedSeq[B] = {
    val b = immutable.IndexedSeq.newBuilder[B]
    b.sizeHint(s.length + 1)
    b.addOne(elem)
    b.addAll(new WrappedString(s))
    b.result()
  }

  /** Alias for `prepended` */
  @`inline` def +: [B >: Char] (elem: B): immutable.IndexedSeq[B] = prepended(elem)

  /** A copy of the String with an char prepended */
  def prepended(c: Char): String =
    new JStringBuilder(s.length + 1).append(c).append(s).toString

  /** Alias for `prepended` */
  @`inline` def +: (c: Char): String = prepended(c)

  /** A copy of the String with all elements from a collection prepended */
  def prependedAll[B >: Char](prefix: Iterable[B]): immutable.IndexedSeq[B] = {
    val b = immutable.IndexedSeq.newBuilder[B]
    val k = prefix.knownSize
    b.sizeHint(s.length + (if(k >= 0) k else 16))
    b.addAll(prefix)
    b.addAll(new WrappedString(s))
    b.result()
  }

  /** Alias for `prependedAll` */
  @`inline` def ++: [B >: Char] (prefix: Iterable[B]): immutable.IndexedSeq[B] = prependedAll(prefix)

  /** A copy of the String with another String prepended */
  def prependedAll(prefix: String): String = prefix + s

  /** Alias for `prependedAll` */
  @`inline` def ++: (prefix: String): String = prependedAll(prefix)

  /** A copy of the String with an element appended */
  def appended[B >: Char](elem: B): immutable.IndexedSeq[B] = {
    val b = immutable.IndexedSeq.newBuilder[B]
    b.sizeHint(s.length + 1)
    b.addAll(new WrappedString(s))
    b.addOne(elem)
    b.result()
  }

  /** Alias for `appended` */
  @`inline` def :+ [B >: Char](elem: B): immutable.IndexedSeq[B] = appended(elem)

  /** A copy of the String with an element appended */
  def appended(c: Char): String =
    new JStringBuilder(s.length + 1).append(s).append(c).toString

  /** Alias for `appended` */
  @`inline` def :+ (c: Char): String = appended(c)

  /** A copy of the String with all elements from a collection appended */
  @`inline` def appendedAll[B >: Char](suffix: Iterable[B]): immutable.IndexedSeq[B] =
    concat(suffix)

  /** Alias for `appendedAll` */
  @`inline` def :++ [B >: Char](suffix: Iterable[B]): immutable.IndexedSeq[B] =
    concat(suffix)

  /** A copy of the String with another String appended */
  @`inline` def appendedAll(suffix: String): String = s + suffix

  /** Alias for `appendedAll` */
  @`inline` def :++ (suffix: String): String = s + suffix

  /** Produces a new collection where a slice of characters in this String is replaced by another collection.
    *
    * Patching at negative indices is the same as patching starting at 0.
    * Patching at indices at or larger than the length of the original String appends the patch to the end.
    * If more values are replaced than actually exist, the excess is ignored.
    *
    *  @param  from     the index of the first replaced char
    *  @param  other    the replacement collection
    *  @param  replaced the number of chars to drop in the original String
    *  @return          a new collection consisting of all chars of this String
    *                   except that `replaced` chars starting from `from` are replaced
    *                   by `other`.
    */
  def patch[B >: Char](from: Int, other: IterableOnce[B], replaced: Int): immutable.IndexedSeq[B] = {
    val len = s.length
    @`inline` def slc(off: Int, length: Int): WrappedString =
      new WrappedString(s.substring(off, off+length))
    val b = immutable.IndexedSeq.newBuilder[B]
    val k = other.knownSize
    if(k >= 0) b.sizeHint(len + k - replaced)
    val chunk1 = if(from > 0) min(from, len) else 0
    if(chunk1 > 0) b.addAll(slc(0, chunk1))
    b ++= other
    val remaining = len - chunk1 - replaced
    if(remaining > 0) b.addAll(slc(len - remaining, remaining))
    b.result()
  }

  /** Produces a new collection where a slice of characters in this String is replaced by another collection.
    *
    * Patching at negative indices is the same as patching starting at 0.
    * Patching at indices at or larger than the length of the original String appends the patch to the end.
    * If more values are replaced than actually exist, the excess is ignored.
    *
    *  @param  from     the index of the first replaced char
    *  @param  other    the replacement string
    *  @param  replaced the number of chars to drop in the original String
    *  @return          a new string consisting of all chars of this String
    *                   except that `replaced` chars starting from `from` are replaced
    *                   by `other`.
    */
  def patch(from: Int, other: IterableOnce[Char], replaced: Int): String =
    patch(from, other.iterator.mkString, replaced)

  /** Produces a new String where a slice of characters in this String is replaced by another String.
    *
    * Patching at negative indices is the same as patching starting at 0.
    * Patching at indices at or larger than the length of the original String appends the patch to the end.
    * If more values are replaced than actually exist, the excess is ignored.
    *
    *  @param  from     the index of the first replaced char
    *  @param  other    the replacement String
    *  @param  replaced the number of chars to drop in the original String
    *  @return          a new String consisting of all chars of this String
    *                   except that `replaced` chars starting from `from` are replaced
    *                   by `other`.
    */
  def patch(from: Int, other: String, replaced: Int): String = {
    val len = s.length
    val sb = new JStringBuilder(len + other.size - replaced)
    val chunk1 = if(from > 0) min(from, len) else 0
    if(chunk1 > 0) sb.append(s, 0, chunk1)
    sb.append(other)
    val remaining = len - chunk1 - replaced
    if(remaining > 0) sb.append(s, len - remaining, len)
    sb.toString
  }

  /** A copy of this string with one single replaced element.
    *  @param  index  the position of the replacement
    *  @param  elem   the replacing element
    *  @return a new string which is a copy of this string with the element at position `index` replaced by `elem`.
    *  @throws IndexOutOfBoundsException if `index` does not satisfy `0 <= index < length`.
    */
  def updated(index: Int, elem: Char): String = {
    val sb = new JStringBuilder(s.length).append(s)
    sb.setCharAt(index, elem)
    sb.toString
  }

  /** Tests whether this String contains the given character.
    *
    *  @param elem  the character to test.
    *  @return     `true` if this String has an element that is equal (as
    *              determined by `==`) to `elem`, `false` otherwise.
    */
  def contains(elem: Char): Boolean = s.indexOf(elem) >= 0

  /** Displays all elements of this string in a string using start, end, and
    * separator strings.
    *
    *  @param start the starting string.
    *  @param sep   the separator string.
    *  @param end   the ending string.
    *  @return      The resulting string
    *               begins with the string `start` and ends with the string
    *               `end`. Inside, the string chars of this string are separated by
    *               the string `sep`.
    */
  final def mkString(start: String, sep: String, end: String): String =
    addString(new StringBuilder(), start, sep, end).toString

  /** Displays all elements of this string in a string using a separator string.
    *
    *  @param sep   the separator string.
    *  @return      In the resulting string
    *               the chars of this string are separated by the string `sep`.
    */
  @inline final def mkString(sep: String): String =
    if (sep.isEmpty || s.length < 2) s
    else mkString("", sep, "")

  /** Returns this String */
  @inline final def mkString: String = s

  /** Appends this string to a string builder. */
  @inline final def addString(b: StringBuilder): StringBuilder = b.append(s)

  /** Appends this string to a string builder using a separator string. */
  @inline final def addString(b: StringBuilder, sep: String): StringBuilder =
    addString(b, "", sep, "")

  /** Appends this string to a string builder using start, end and separator strings. */
  final def addString(b: StringBuilder, start: String, sep: String, end: String): StringBuilder = {
    val jsb = b.underlying
    if (start.length != 0) jsb.append(start)
    val len = s.length
    if (len != 0) {
      if (sep.isEmpty) jsb.append(s)
      else {
        jsb.ensureCapacity(jsb.length + len + end.length + (len - 1) * sep.length)
        jsb.append(s.charAt(0))
        var i = 1
        while (i < len) {
          jsb.append(sep)
          jsb.append(s.charAt(i))
          i += 1
        }
      }
    }
    if (end.length != 0) jsb.append(end)
    b
  }

  /** Selects an interval of elements.  The returned string is made up
    *  of all elements `x` which satisfy the invariant:
    *  {{{
    *    from <= indexOf(x) < until
    *  }}}
    *
    *  @param from   the lowest index to include from this $coll.
    *  @param until  the lowest index to EXCLUDE from this $coll.
    *  @return  a string containing the elements greater than or equal to
    *           index `from` extending up to (but not including) index `until`
    *           of this $coll.
    */
  def slice(from: Int, until: Int): String = {
    val start = from max 0
    val end   = until min s.length

    if (start >= end) ""
    else s.substring(start, end)
  }

  // Note: String.repeat is added in JDK 11.
  /** Return the current string concatenated `n` times.
    */
  def *(n: Int): String = {
    val sb = new JStringBuilder(s.length * n)
    var i = 0
    while (i < n) {
      sb.append(s)
      i += 1
    }
    sb.toString
  }

  @inline private[this] def isLineBreak(c: Char) = c == LF || c == FF

  /**
    *  Strip trailing line end character from this string if it has one.
    *
    *  A line end character is one of
    *  - `LF` - line feed   (`0x0A` hex)
    *  - `FF` - form feed   (`0x0C` hex)
    *
    *  If a line feed character `LF` is preceded by a carriage return `CR`
    *  (`0x0D` hex), the `CR` character is also stripped (Windows convention).
    */
  def stripLineEnd: String = {
    val len = s.length
    if (len == 0) s
    else {
      val last = apply(len - 1)
      if (isLineBreak(last))
        s.substring(0, if (last == LF && len >= 2 && apply(len - 2) == CR) len - 2 else len - 1)
      else
        s
    }
  }

  /** Return all lines in this string in an iterator, including trailing
    *  line end characters.
    *
    *  This method is analogous to `s.split(EOL).toIterator`,
    *  except that any existing line endings are preserved in the result strings,
    *  and the empty string yields an empty iterator.
    *
    *  A line end character is one of
    *  - `LF` - line feed   (`0x0A`)
    *  - `FF` - form feed   (`0x0C`)
    */
  def linesWithSeparators: Iterator[String] = new AbstractIterator[String] {
    private[this] val len = s.length
    private[this] var index = 0
    def hasNext: Boolean = index < len
    def next(): String = {
      if (index >= len) Iterator.empty.next()
      val start = index
      while (index < len && !isLineBreak(apply(index))) index += 1
      index += 1
      s.substring(start, index min len)
    }
  }

  /** Return all lines in this string in an iterator, excluding trailing line
    *  end characters; i.e., apply `.stripLineEnd` to all lines
    *  returned by `linesWithSeparators`.
    */
  def lines: Iterator[String] =
    linesWithSeparators map (_.stripLineEnd)

  /** Returns this string with first character converted to upper case.
    * If the first character of the string is capitalized, it is returned unchanged.
    * This method does not convert characters outside the Basic Multilingual Plane (BMP).
    */
  def capitalize: String =
    if (s == null || s.length == 0 || !s.charAt(0).isLower) s
    else updated(0, s.charAt(0).toUpper)

  /** Returns this string with the given `prefix` stripped. If this string does not
    *  start with `prefix`, it is returned unchanged.
    */
  def stripPrefix(prefix: String) =
    if (s startsWith prefix) s.substring(prefix.length)
    else s

  /** Returns this string with the given `suffix` stripped. If this string does not
    *  end with `suffix`, it is returned unchanged.
    */
  def stripSuffix(suffix: String) =
    if (s endsWith suffix) s.substring(0, s.length - suffix.length)
    else s

  /** Replace all literal occurrences of `literal` with the literal string `replacement`.
    *  This method is equivalent to [[java.lang.String#replace]].
    *
    *  @param    literal     the string which should be replaced everywhere it occurs
    *  @param    replacement the replacement string
    *  @return               the resulting string
    */
  def replaceAllLiterally(literal: String, replacement: String): String = s.replace(literal, replacement)

  /** For every line in this string:
    *
    *  Strip a leading prefix consisting of blanks or control characters
    *  followed by `marginChar` from the line.
    */
  def stripMargin(marginChar: Char): String = {
    val sb = new JStringBuilder(s.length)
    for (line <- linesWithSeparators) {
      val len = line.length
      var index = 0
      while (index < len && line.charAt(index) <= ' ') index += 1
      sb.append {
        if (index < len && line.charAt(index) == marginChar) line.substring(index + 1)
        else line
      }
    }
    sb.toString
  }

  /** For every line in this string:
    *
    *  Strip a leading prefix consisting of blanks or control characters
    *  followed by `|` from the line.
    */
  def stripMargin: String = stripMargin('|')

  private[this] def escape(ch: Char): String = if (
    (ch >= 'a') && (ch <= 'z') ||
      (ch >= 'A') && (ch <= 'Z') ||
      (ch >= '0' && ch <= '9')) ch.toString
  else "\\" + ch

  /** Split this string around the separator character
    *
    * If this string is the empty string, returns an array of strings
    * that contains a single empty string.
    *
    * If this string is not the empty string, returns an array containing
    * the substrings terminated by the start of the string, the end of the
    * string or the separator character, excluding empty trailing substrings
    *
    * If the separator character is a surrogate character, only split on
    * matching surrogate characters if they are not part of a surrogate pair
    *
    * The behaviour follows, and is implemented in terms of <a href="http://docs.oracle.com/javase/7/docs/api/java/lang/String.html#split%28java.lang.String%29">String.split(re: String)</a>
    *
    *
    * @example {{{
    * "a.b".split('.') //returns Array("a", "b")
    *
    * //splitting the empty string always returns the array with a single
    * //empty string
    * "".split('.') //returns Array("")
    *
    * //only trailing empty substrings are removed
    * "a.".split('.') //returns Array("a")
    * ".a.".split('.') //returns Array("", "a")
    * "..a..".split('.') //returns Array("", "", "a")
    *
    * //all parts are empty and trailing
    * ".".split('.') //returns Array()
    * "..".split('.') //returns Array()
    *
    * //surrogate pairs
    * val high = 0xD852.toChar
    * val low = 0xDF62.toChar
    * val highstring = high.toString
    * val lowstring = low.toString
    *
    * //well-formed surrogate pairs are not split
    * val highlow = highstring + lowstring
    * highlow.split(high) //returns Array(highlow)
    *
    * //bare surrogate characters are split
    * val bare = "_" + highstring + "_"
    * bare.split(high) //returns Array("_", "_")
    *
    *  }}}
    *
    * @param separator the character used as a delimiter
    */
  def split(separator: Char): Array[String] = s.split(escape(separator))

  @throws(classOf[java.util.regex.PatternSyntaxException])
  def split(separators: Array[Char]): Array[String] = {
    val re = separators.foldLeft("[")(_+escape(_)) + "]"
    s.split(re)
  }

  /** You can follow a string with `.r`, turning it into a `Regex`. E.g.
    *
    *  `"""A\w*""".r`   is the regular expression for identifiers starting with `A`.
    */
  def r: Regex = r()

  /** You can follow a string with `.r(g1, ... , gn)`, turning it into a `Regex`,
    *  with group names g1 through gn.
    *
    *  `"""(\d\d)-(\d\d)-(\d\d\d\d)""".r("month", "day", "year")` matches dates
    *  and provides its subcomponents through groups named "month", "day" and
    *  "year".
    *
    *  @param groupNames The names of the groups in the pattern, in the order they appear.
    */
  def r(groupNames: String*): Regex = new Regex(s, groupNames: _*)

  /**
   * @throws java.lang.IllegalArgumentException  If the string does not contain a parsable `Boolean`.
   */
  def toBoolean: Boolean               = toBooleanImpl(s)

  /**
   * Try to parse as a `Boolean`
   * @return `Some(true)` if the string is "true" case insensitive,
   * `Some(false)` if the string is "false" case insensitive,
   * and `None` if the string is anything else
   * @throws java.lang.NullPointerException if the string is `null`
   */
  def toBooleanOption: Option[Boolean] = StringParsers.parseBool(s)

  /**
    * Parse as a `Byte` (string must contain only decimal digits and optional leading `-` or `+`).
    * @throws java.lang.NumberFormatException  If the string does not contain a parsable `Byte`.
    */
  def toByte: Byte                     = java.lang.Byte.parseByte(s)

  /**
   * Try to parse as a `Byte`
   * @return `Some(value)` if the string contains a valid byte value, otherwise `None`
   * @throws java.lang.NullPointerException if the string is `null`
   */
  def toByteOption: Option[Byte]       = StringParsers.parseByte(s)

  /**
    * Parse as a `Short` (string must contain only decimal digits and optional leading `-` or `+`).
    * @throws java.lang.NumberFormatException  If the string does not contain a parsable `Short`.
    */
  def toShort: Short                   = java.lang.Short.parseShort(s)

  /**
   * Try to parse as a `Short`
   * @return `Some(value)` if the string contains a valid short value, otherwise `None`
   * @throws java.lang.NullPointerException if the string is `null`
   */
  def toShortOption: Option[Short]     = StringParsers.parseShort(s)

  /**
    * Parse as an `Int` (string must contain only decimal digits and optional leading `-` or `+`).
    * @throws java.lang.NumberFormatException  If the string does not contain a parsable `Int`.
    */
  def toInt: Int                       = java.lang.Integer.parseInt(s)

  /**
   * Try to parse as an `Int`
   * @return `Some(value)` if the string contains a valid Int value, otherwise `None`
   * @throws java.lang.NullPointerException if the string is `null`
   */
  def toIntOption: Option[Int]         = StringParsers.parseInt(s)

  /**
    * Parse as a `Long` (string must contain only decimal digits and optional leading `-` or `+`).
    * @throws java.lang.NumberFormatException  If the string does not contain a parsable `Long`.
    */
  def toLong: Long                     = java.lang.Long.parseLong(s)

  /**
   * Try to parse as a `Long`
   * @return `Some(value)` if the string contains a valid long value, otherwise `None`
   * @throws java.lang.NullPointerException if the string is `null`
   */
  def toLongOption: Option[Long]       = StringParsers.parseLong(s)

  /**
    * Parse as a `Float` (surrounding whitespace is removed with a `trim`).
    * @throws java.lang.NumberFormatException  If the string does not contain a parsable `Float`.
    * @throws java.lang.NullPointerException  If the string is null.
    */
  def toFloat: Float                   = java.lang.Float.parseFloat(s)

  /**
   * Try to parse as a `Float`
   * @return `Some(value)` if the string is a parsable `Float`, `None` otherwise
   * @throws java.lang.NullPointerException If the string is null
   */
  def toFloatOption: Option[Float]     = StringParsers.parseFloat(s)

  /**
    * Parse as a `Double` (surrounding whitespace is removed with a `trim`).
    * @throws java.lang.NumberFormatException  If the string does not contain a parsable `Double`.
    * @throws java.lang.NullPointerException  If the string is null.
    */
  def toDouble: Double                 = java.lang.Double.parseDouble(s)

  /**
   * Try to parse as a `Double`
   * @return `Some(value)` if the string is a parsable `Double`, `None` otherwise
   * @throws java.lang.NullPointerException If the string is null
   */
  def toDoubleOption: Option[Double]   = StringParsers.parseDouble(s)

  private[this] def toBooleanImpl(s: String): Boolean =
    if (s == null) throw new IllegalArgumentException("For input string: \"null\"")
    else if (s.equalsIgnoreCase("true")) true
    else if (s.equalsIgnoreCase("false")) false
    else throw new IllegalArgumentException("For input string: \""+s+"\"")

  def toArray[B >: Char](implicit tag: ClassTag[B]): Array[B] =
    if (tag == ClassTag.Char) s.toCharArray.asInstanceOf[Array[B]]
    else new WrappedString(s).toArray[B]

  private[this] def unwrapArg(arg: Any): AnyRef = arg match {
    case x: ScalaNumber => x.underlying
    case x              => x.asInstanceOf[AnyRef]
  }

  /** Uses the underlying string as a pattern (in a fashion similar to
    *  printf in C), and uses the supplied arguments to fill in the
    *  holes.
    *
    *    The interpretation of the formatting patterns is described in
    *    [[java.util.Formatter]], with the addition that
    *    classes deriving from `ScalaNumber` (such as [[scala.BigInt]] and
    *    [[scala.BigDecimal]]) are unwrapped to pass a type which `Formatter`
    *    understands.
    *
    *  @param args the arguments used to instantiating the pattern.
    *  @throws java.lang.IllegalArgumentException
    */
  def format(args : Any*): String =
    java.lang.String.format(s, args map unwrapArg: _*)

  /** Like `format(args*)` but takes an initial `Locale` parameter
    *  which influences formatting as in `java.lang.String`'s format.
    *
    *    The interpretation of the formatting patterns is described in
    *    [[java.util.Formatter]], with the addition that
    *    classes deriving from `ScalaNumber` (such as `scala.BigInt` and
    *    `scala.BigDecimal`) are unwrapped to pass a type which `Formatter`
    *    understands.
    *
    *  @param l    an instance of `java.util.Locale`
    *  @param args the arguments used to instantiating the pattern.
    *  @throws java.lang.IllegalArgumentException
    */
  def formatLocal(l: java.util.Locale, args: Any*): String =
    java.lang.String.format(l, s, args map unwrapArg: _*)

  def compare(that: String): Int = s.compareTo(that)

  /** Returns true if `this` is less than `that` */
  def < (that: String): Boolean = compare(that) <  0

  /** Returns true if `this` is greater than `that`. */
  def > (that: String): Boolean = compare(that) >  0

  /** Returns true if `this` is less than or equal to `that`. */
  def <= (that: String): Boolean = compare(that) <= 0

  /** Returns true if `this` is greater than or equal to `that`. */
  def >= (that: String): Boolean = compare(that) >= 0

  /** Counts the number of chars in this string which satisfy a predicate */
  def count(p: (Char) => Boolean): Int = {
    var i, res = 0
    val len = s.length
    while(i < len) {
      if(p(s.charAt(i))) res += 1
      i += 1
    }
    res
  }

  /** Apply `f` to each element for its side effects.
    * Note: [U] parameter needed to help scalac's type inference.
    */
  def foreach[U](f: Char => U): Unit = {
    val len = s.length
    var i = 0
    while(i < len) {
      f(s.charAt(i))
      i += 1
    }
  }

  /** Tests whether a predicate holds for all chars of this string.
    *
    *  @param   p     the predicate used to test elements.
    *  @return        `true` if this string is empty or the given predicate `p`
    *                 holds for all chars of this string, otherwise `false`.
    */
  def forall(f: Char => Boolean): Boolean = {
    var i = 0
    val len = s.length
    while(i < len) {
      if(!f(s.charAt(i))) return false
      i += 1
    }
    true
  }

  /** Applies a binary operator to a start value and all chars of this string,
    * going left to right.
    *
    *  @param   z    the start value.
    *  @param   op   the binary operator.
    *  @tparam  B    the result type of the binary operator.
    *  @return  the result of inserting `op` between consecutive chars of this string,
    *           going left to right with the start value `z` on the left:
    *           {{{
    *             op(...op(z, x_1), x_2, ..., x_n)
    *           }}}
    *           where `x,,1,,, ..., x,,n,,` are the chars of this string.
    *           Returns `z` if this string is empty.
    */
  def foldLeft[B](z: B)(op: (B, Char) => B): B = {
    var v = z
    var i = 0
    val len = s.length
    while(i < len) {
      v = op(v, s.charAt(i))
      i += 1
    }
    v
  }

  /** Applies a binary operator to all chars of this string and a start value,
    * going right to left.
    *
    *  @param   z    the start value.
    *  @param   op   the binary operator.
    *  @tparam  B    the result type of the binary operator.
    *  @return  the result of inserting `op` between consecutive chars of this string,
    *           going right to left with the start value `z` on the right:
    *           {{{
    *             op(x_1, op(x_2, ... op(x_n, z)...))
    *           }}}
    *           where `x,,1,,, ..., x,,n,,` are the chars of this string.
    *           Returns `z` if this string is empty.
    */
  def foldRight[B](z: B)(op: (Char, B) => B): B = {
    var v = z
    var i = s.length - 1
    while(i >= 0) {
      v = op(s.charAt(i), v)
      i -= 1
    }
    v
  }

  /** Folds the chars of this string using the specified associative binary operator.
    *
    *  @tparam A1     a type parameter for the binary operator, a supertype of Char.
    *  @param z       a neutral element for the fold operation; may be added to the result
    *                 an arbitrary number of times, and must not change the result (e.g., `Nil` for list concatenation,
    *                 0 for addition, or 1 for multiplication).
    *  @param op      a binary operator that must be associative.
    *  @return        the result of applying the fold operator `op` between all the chars and `z`, or `z` if this string is empty.
    */
  @`inline` def fold[A1 >: Char](z: A1)(op: (A1, A1) => A1): A1 = foldLeft(z)(op)

  /** Selects the first char of this string.
    *  @return  the first char of this string.
    *  @throws NoSuchElementException if the string is empty.
    */
  def head: Char = if(s.isEmpty) throw new NoSuchElementException("head of empty String") else s.charAt(0)

  /** Optionally selects the first char.
    *  @return  the first char of this string if it is nonempty,
    *           `None` if it is empty.
    */
  def headOption: Option[Char] =
    if(s.isEmpty) None else Some(s.charAt(0))

  /** Selects the last char of this string.
    *  @return  the last char of this string.
    *  @throws NoSuchElementException if the string is empty.
    */
  def last: Char = if(s.isEmpty) throw new NoSuchElementException("last of empty String") else s.charAt(s.length-1)

  /** Optionally selects the last char.
    *  @return  the last char of this string if it is nonempty,
    *           `None` if it is empty.
    */
  def lastOption: Option[Char] =
    if(s.isEmpty) None else Some(s.charAt(s.length-1))

  /** Produces the range of all indices of this string.
    *
    *  @return  a `Range` value from `0` to one less than the length of this string.
    */
  def indices: Range = Range(0, s.length)

  /** Iterator can be used only once */
  def iterator(): Iterator[Char] = new StringIterator(s)

  /** Tests whether the string is not empty. */
  @`inline` def nonEmpty: Boolean = !s.isEmpty

  /** Returns new sequence with elements in reversed order. */
  def reverse: String = new JStringBuilder(s).reverse().toString

  /** An iterator yielding chars in reversed order.
    *
    * Note: `xs.reverseIterator` is the same as `xs.reverse.iterator` but implemented more efficiently.
    *
    *  @return  an iterator yielding the chars of this string in reversed order
    */
  def reverseIterator(): Iterator[Char] = new ReverseIterator(s)

  /** Creates a non-strict filter of this string.
    *
    *  Note: the difference between `c filter p` and `c withFilter p` is that
    *        the former creates a new string, whereas the latter only
    *        restricts the domain of subsequent `map`, `flatMap`, `foreach`,
    *        and `withFilter` operations.
    *
    *  @param p   the predicate used to test elements.
    *  @return    an object of class `StringOps.WithFilter`, which supports
    *             `map`, `flatMap`, `foreach`, and `withFilter` operations.
    *             All these operations apply to those chars of this string
    *             which satisfy the predicate `p`.
    */
  def withFilter(p: Char => Boolean): StringOps.WithFilter = new StringOps.WithFilter(p, s)

  /** The rest of the string without its first char. */
  def tail: String = slice(1, s.length)

  /** The initial part of the string without its last char. */
  def init: String = slice(0, s.length-1)

  /** A string containing the first `n` chars of this string. */
  def take(n: Int): String = slice(0, min(n, s.length))

  /** The rest of the string without its `n` first chars. */
  def drop(n: Int): String = slice(min(n, s.length), s.length)

  /** An string containing the last `n` chars of this string. */
  def takeRight(n: Int): String = drop(s.length - max(n, 0))

  /** The rest of the string without its `n` last chars. */
  def dropRight(n: Int): String = take(s.length - max(n, 0))

  /** Iterates over the tails of this string. The first value will be this
    * string and the final one will be an empty string, with the intervening
    * values the results of successive applications of `tail`.
    *
    *  @return   an iterator over all the tails of this string
    */
  def tails: Iterator[String] = iterateUntilEmpty(_.tail)

  /** Iterates over the inits of this string. The first value will be this
    * string and the final one will be an empty string, with the intervening
    * values the results of successive applications of `init`.
    *
    *  @return  an iterator over all the inits of this string
    */
  def inits: Iterator[String] = iterateUntilEmpty(_.init)

  // A helper for tails and inits.
  private[this] def iterateUntilEmpty(f: String => String): Iterator[String] =
    Iterator.iterate(s)(f).takeWhile(x => !x.isEmpty) ++ Iterator.single("")

  /** Selects all chars of this string which satisfy a predicate. */
  def filter(pred: Char => Boolean): String = {
    val len = s.length
    val sb = new JStringBuilder(len)
    var i = 0
    while (i < len) {
      val x = s.charAt(i)
      if(pred(x)) sb.append(x)
      i += 1
    }
    if(len == sb.length()) s else sb.toString
  }

  /** Selects all chars of this string which do not satisfy a predicate. */
  @`inline` def filterNot(pred: Char => Boolean): String = filter(c => !pred(c))

  /** Copy chars of this string to an array.
    * Fills the given array `xs` starting at index `start` with at most `len` chars.
    * Copying will stop once either the entire string has been copied,
    * or the end of the array is reached or `len` chars have been copied.
    *
    *  @param  xs     the array to fill.
    *  @param  start  the starting index.
    *  @param  len    the maximal number of elements to copy.
    */
  def copyToArray(xs: Array[Char], start: Int, len: Int): Unit =
    s.getChars(0, min(min(s.length, len), xs.length-start), xs, start)

  /** Copy chars of this string to an array.
    * Fills the given array `xs` starting at index `start`.
    * Copying will stop once either the entire string has been copied
    * or the end of the array is reached
    *
    *  @param  xs     the array to fill.
    *  @param  start  the starting index.
    *  @param  len    the maximal number of elements to copy.
    */
  @`inline` def copyToArray(xs: Array[Char], start: Int): Unit =
    copyToArray(xs, start, Int.MaxValue)

  /** Finds index of the first char satisfying some predicate after or at some start index.
    *
    *  @param   p     the predicate used to test elements.
    *  @param   from   the start index
    *  @return  the index `>= from` of the first element of this string that satisfies the predicate `p`,
    *           or `-1`, if none exists.
    */
  def indexWhere(p: Char => Boolean, from: Int = 0): Int = {
    val len = s.length
    var i = from
    while(i < len) {
      if(p(s.charAt(i))) return i
      i += 1
    }
    -1
  }

  /** Finds index of the last char satisfying some predicate before or at some end index.
    *
    *  @param   p     the predicate used to test elements.
    *  @param   end   the end index
    *  @return  the index `<= end` of the last element of this string that satisfies the predicate `p`,
    *           or `-1`, if none exists.
    */
  def lastIndexWhere(p: Char => Boolean, end: Int = Int.MaxValue): Int = {
    val len = s.length
    var i = min(end, len-1)
    while(i >= 0) {
      if(p(s.charAt(i))) return i
      i -= 1
    }
    -1
  }

  /** Tests whether a predicate holds for at least one char of this string. */
  def exists(p: Char => Boolean): Boolean = indexWhere(p) != -1

  /** Finds the first char of the string satisfying a predicate, if any.
    *
    *  @param p       the predicate used to test elements.
    *  @return        an option value containing the first element in the string
    *                 that satisfies `p`, or `None` if none exists.
    */
  def find(p: Char => Boolean): Option[Char] = indexWhere(p) match {
    case -1 => None
    case i => Some(s.charAt(i))
  }

  /** Drops longest prefix of chars that satisfy a predicate.
    *
    *  @param   p  The predicate used to test elements.
    *  @return  the longest suffix of this string whose first element
    *           does not satisfy the predicate `p`.
    */
  def dropWhile(p: Char => Boolean): String = indexWhere(c => !p(c)) match {
    case -1 => ""
    case i => s.substring(i)
  }

  /** Takes longest prefix of chars that satisfy a predicate. */
  def takeWhile(p: Char => Boolean): String = indexWhere(c => !p(c)) match {
    case -1 => s
    case i => s.substring(0, i)
  }

  /** Splits this string into two at a given position.
    * Note: `c splitAt n` is equivalent to `(c take n, c drop n)`.
    *
    *  @param n the position at which to split.
    *  @return  a pair of strings consisting of the first `n`
    *           chars of this string, and the other chars.
    */
  def splitAt(n: Int): (String, String) = (take(n), drop(n))

  /** Splits this string into a prefix/suffix pair according to a predicate.
    *
    *  Note: `c span p`  is equivalent to (but more efficient than)
    *  `(c takeWhile p, c dropWhile p)`, provided the evaluation of the
    *  predicate `p` does not cause any side-effects.
    *
    *  @param p the test predicate
    *  @return  a pair consisting of the longest prefix of this string whose
    *           chars all satisfy `p`, and the rest of this string.
    */
  def span(p: Char => Boolean): (String, String) = indexWhere(c => !p(c)) match {
    case -1 => (s, "")
    case i => (s.substring(0, i), s.substring(i))
  }

  /** Partitions elements in fixed size strings.
    *  @see [[scala.collection.Iterator]], method `grouped`
    *
    *  @param size the number of elements per group
    *  @return An iterator producing strings of size `size`, except the
    *          last will be less than size `size` if the elements don't divide evenly.
    */
  def grouped(size: Int): Iterator[String] = new StringOps.GroupedIterator(s, size)

  /** A pair of, first, all chars that satisfy predicate `p` and, second, all chars that do not. */
  def partition(p: Char => Boolean): (String, String) = {
    val res1, res2 = new JStringBuilder
    var i = 0
    val len = s.length
    while(i < len) {
      val x = s.charAt(i)
      (if(p(x)) res1 else res2).append(x)
      i += 1
    }
    (res1.toString, res2.toString)
  }


  /* ************************************************************************************************************
     The remaining methods are provided for completeness but they delegate to WrappedString implementations which
     may not provide the best possible performance. We need them in `StringOps` because their return type
     mentions `C` (which is `String` in `StringOps` and `WrappedString` in `WrappedString`).
     ************************************************************************************************************ */


  /** Computes the multiset difference between this string and another sequence.
    *
    *  @param that   the sequence of chars to remove
    *  @return       a new string which contains all chars of this string
    *                except some of occurrences of elements that also appear in `that`.
    *                If an element value `x` appears
    *                ''n'' times in `that`, then the first ''n'' occurrences of `x` will not form
    *                part of the result, but any following occurrences will.
    */
  @deprecated("Use `new WrappedString(s).diff(...).self` instead of `s.diff(...)`", "2.13.0")
  def diff(that: Seq[_ >: Char]): String = new WrappedString(s).diff(that).self

  /** Computes the multiset intersection between this string and another sequence.
    *
    *  @param that   the sequence of chars to intersect with.
    *  @return       a new string which contains all chars of this string
    *                which also appear in `that`.
    *                If an element value `x` appears
    *                ''n'' times in `that`, then the first ''n'' occurrences of `x` will be retained
    *                in the result, but any following occurrences will be omitted.
    */
  @deprecated("Use `new WrappedString(s).intersect(...).self` instead of `s.intersect(...)`", "2.13.0")
  def intersect(that: Seq[_ >: Char]): String = new WrappedString(s).intersect(that).self

  /** Selects all distinct chars of this string ignoring the duplicates. */
  @deprecated("Use `new WrappedString(s).distinct.self` instead of `s.distinct`", "2.13.0")
  def distinct: String = new WrappedString(s).distinct.self

  /** Selects all distinct chars of this string ignoring the duplicates as determined by `==` after applying
    * the transforming function `f`.
    *
    * @param f The transforming function whose result is used to determine the uniqueness of each element
    * @tparam B the type of the elements after being transformed by `f`
    * @return a new string consisting of all the chars of this string without duplicates.
    */
  @deprecated("Use `new WrappedString(s).distinctBy(...).self` instead of `s.distinctBy(...)`", "2.13.0")
  def distinctBy[B](f: Char => B): String = new WrappedString(s).distinctBy(f).self

  /** Sorts the characters of this string according to an Ordering.
    *
    *  The sort is stable. That is, elements that are equal (as determined by
    *  `ord.compare`) appear in the same order in the sorted sequence as in the original.
    *
    *  @see [[scala.math.Ordering]]
    *
    *  @param  ord the ordering to be used to compare elements.
    *  @return     a string consisting of the chars of this string
    *              sorted according to the ordering `ord`.
    */
  @deprecated("Use `new WrappedString(s).sorted.self` instead of `s.sorted`", "2.13.0")
  def sorted[B >: Char](implicit ord: Ordering[B]): String = new WrappedString(s).sorted(ord).self

  /** Sorts this string according to a comparison function.
    *
    *  The sort is stable. That is, elements that are equal (as determined by
    *  `lt`) appear in the same order in the sorted sequence as in the original.
    *
    *  @param  lt  the comparison function which tests whether
    *              its first argument precedes its second argument in
    *              the desired ordering.
    *  @return     a string consisting of the elements of this string
    *              sorted according to the comparison function `lt`.
    */
  @deprecated("Use `new WrappedString(s).sortWith(...).self` instead of `s.sortWith(...)`", "2.13.0")
  def sortWith(lt: (Char, Char) => Boolean): String = new WrappedString(s).sortWith(lt).self

  /** Sorts this string according to the Ordering which results from transforming
    * an implicitly given Ordering with a transformation function.
    *
    * The sort is stable. That is, elements that are equal (as determined by
    * `ord.compare`) appear in the same order in the sorted sequence as in the original.
    *
    *  @see [[scala.math.Ordering]]
    *  @param   f the transformation function mapping elements
    *           to some other domain `B`.
    *  @param   ord the ordering assumed on domain `B`.
    *  @tparam  B the target type of the transformation `f`, and the type where
    *           the ordering `ord` is defined.
    *  @return  a string consisting of the chars of this string
    *           sorted according to the ordering where `x < y` if
    *           `ord.lt(f(x), f(y))`.
    */
  @deprecated("Use `new WrappedString(s).sortBy(...).self` instead of `s.sortBy(...)`", "2.13.0")
  def sortBy[B](f: Char => B)(implicit ord: Ordering[B]): String = new WrappedString(s).sortBy(f)(ord).self

  /** Partitions this string into a map of strings according to some discriminator function.
    *
    *  @param f     the discriminator function.
    *  @tparam K    the type of keys returned by the discriminator function.
    *  @return      A map from keys to strings such that the following invariant holds:
    *               {{{
    *                 (xs groupBy f)(k) = xs filter (x => f(x) == k)
    *               }}}
    *               That is, every key `k` is bound to a string of those elements `x`
    *               for which `f(x)` equals `k`.
    *
    */
  @deprecated("Use `new WrappedString(s).groupBy(...).mapValues(_.self)` instead of `s.groupBy(...)`", "2.13.0")
  def groupBy[K](f: Char => K): immutable.Map[K, String] = new WrappedString(s).groupBy(f).mapValues(_.self).toMap

  /** Groups chars in fixed size blocks by passing a "sliding window"
    *  over them (as opposed to partitioning them, as is done in grouped.)
    *  @see [[scala.collection.Iterator]], method `sliding`
    *
    *  @param size the number of chars per group
    *  @param step the distance between the first chars of successive groups
    *  @return An iterator producing strings of size `size`, except the
    *          last element (which may be the only element) will be truncated
    *          if there are fewer than `size` chars remaining to be grouped.
    */
  @deprecated("Use `new WrappedString(s).sliding(...).map(_.self)` instead of `s.sliding(...)`", "2.13.0")
  def sliding(size: Int, step: Int = 1): Iterator[String] = new WrappedString(s).sliding(size, step).map(_.self)

  /** Iterates over combinations.  A _combination_ of length `n` is a subsequence of
    *  the original string, with the chars taken in order.  Thus, `"xy"` and `"yy"`
    *  are both length-2 combinations of `"xyy"`, but `"yx"` is not.  If there is
    *  more than one way to generate the same subsequence, only one will be returned.
    *
    *  For example, `"xyyy"` has three different ways to generate `"xy"` depending on
    *  whether the first, second, or third `"y"` is selected.  However, since all are
    *  identical, only one will be chosen.  Which of the three will be taken is an
    *  implementation detail that is not defined.
    *
    *  @return   An Iterator which traverses the possible n-element combinations of this string.
    *  @example  `"abbbc".combinations(2) = Iterator(ab, ac, bb, bc)`
    */
  @deprecated("Use `new WrappedString(s).combinations(...).map(_.self)` instead of `s.combinations(...)`", "2.13.0")
  def combinations(n: Int): Iterator[String] = new WrappedString(s).combinations(n).map(_.self)

  /** Iterates over distinct permutations.
    *
    *  @return   An Iterator which traverses the distinct permutations of this string.
    *  @example  `"abb".permutations = Iterator(abb, bab, bba)`
    */
  @deprecated("Use `new WrappedString(s).permutations(...).map(_.self)` instead of `s.permutations(...)`", "2.13.0")
  def permutations: Iterator[String] = new WrappedString(s).permutations.map(_.self)
}

case class StringView(s: String) extends AbstractIndexedSeqView[Char] {
  def length = s.length
  @throws[StringIndexOutOfBoundsException]
  def apply(n: Int) = s.charAt(n)
  override protected[this] def className = "StringView"
}
