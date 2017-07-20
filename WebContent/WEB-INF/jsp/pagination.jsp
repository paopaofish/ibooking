<nav id="ib_pag_nav">
    <ul class="pagination">
        <li><a href="${destPage}?page=1" >&laquo;</a></li>
        <c:forEach var="idx" begin="${startPage}" end="${endPage}">
            <c:if test="${idx == currPage}">
                <li class="active"><a href="#">${idx} <span class="sr-only">(current)</span></a></li>
            </c:if>
            <c:if test="${idx != currPage}">
                <li><a href="${destPage}?page=${idx}">${idx} </a></li>
            </c:if>
        </c:forEach>
        <li><a href="${destPage}?page=${maxPage}">&raquo;</a></li>
    </ul>
</nav>