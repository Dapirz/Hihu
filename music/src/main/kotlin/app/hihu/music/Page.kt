package app.hihu.music

/**
 * One page of a longer list. [next] fetches the page after it, or is null on the last page; how
 * the provider remembers where it was stays hidden inside it. [total] is the size of the whole
 * list when the provider knows it, which it usually only tells on the first page.
 */
class Page<T>(val items: List<T>, val next: (suspend () -> Page<T>)?, val total: Long? = null)
