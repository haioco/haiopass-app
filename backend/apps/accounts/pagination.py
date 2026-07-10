from rest_framework.pagination import PageNumberPagination
from rest_framework.response import Response


class CustomPageNumberPagination(PageNumberPagination):
    page_size = 20
    page_size_query_param = 'page_size'
    max_page_size = 100

    def get_paginated_response(self, data):
        return Response({
            'data': data,
            'total_items': self.page.paginator.count,
            'current_items': len(data),
            'per_page': self.page_size,
            'current_page': self.page.number,
            'last_page': self.page.paginator.num_pages,
        })