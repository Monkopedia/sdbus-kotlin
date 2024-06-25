package header

interface Resource {
    /**
     * Releases this resource and any child resources it may have.
     */
    fun release()
}
