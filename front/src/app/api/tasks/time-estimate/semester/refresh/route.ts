import { NextRequest, NextResponse } from 'next/server';

export async function POST(request: NextRequest) {
  const authToken = request.headers.get('Authorization');

  if (!authToken) {
    return NextResponse.json({ message: 'Authorization token is missing' }, { status: 401 });
  }

  // Получаем query параметр 'date' из URL запроса
  const { searchParams } = new URL(request.url);
  const date = searchParams.get('date');

  if (!date) {
    return NextResponse.json({ message: "Query parameter 'date' is missing" }, { status: 400 });
  }

  // Убедимся, что дата в формате YYYY-MM-DD
  const dateRegex = /^\d{4}-\d{2}-\d{2}$/;
  if (!dateRegex.test(date)) {
    return NextResponse.json({ message: "Invalid date format. Expected YYYY-MM-DD." }, { status: 400 });
  }

  const backendUrl = `http://localhost:8080/api/tasks/time-estimate/semester/refresh?date=${date}`;
  console.log(`[API PROXY REFRESH ESTIMATES] Proxying to: ${backendUrl}`);

  try {
    const backendResponse = await fetch(backendUrl, {
      method: 'POST',
      headers: {
        'Authorization': authToken,
      },
    });

    // Бэкенд может вернуть пустой ответ при успехе, или список TaskTimeEstimateResponse
    // Если тело ответа пустое, backendResponse.json() вызовет ошибку.
    // Поэтому сначала проверяем Content-Type или длину ответа
    const contentType = backendResponse.headers.get("content-type");
    let data;
    if (contentType && contentType.includes("application/json")) {
        data = await backendResponse.json();
    } else {
        // Если не JSON или пустой ответ, но статус ОК, считаем успехом
        // Бэкенд возвращает List<TaskTimeEstimateResponse> или HTTP 200 OK с пустым телом при ошибке в конкретных задачах, но сам эндпоинт отрабатывает
        // Для простоты, если статус ОК, просто проксируем статус
        // В идеале, бэкенд всегда должен возвращать консистентный JSON
        if (backendResponse.ok) {
            return new NextResponse(null, { status: backendResponse.status }); // Или можно вернуть data, если там что-то ожидается
        }
        // Если не ОК и не JSON, пытаемся получить текстовое сообщение об ошибке
        const errorText = await backendResponse.text();
        data = { message: errorText || 'Error from backend' }; 
    }

    if (!backendResponse.ok) {
      console.error(`[API PROXY REFRESH ESTIMATES] Error from backend (${backendResponse.status}):`, data.message || backendResponse.statusText);
      return NextResponse.json(
        { message: data.message || 'Error from backend' },
        { status: backendResponse.status }
      );
    }
    console.log("[API PROXY REFRESH ESTIMATES] Successfully proxied. Backend status:", backendResponse.status);
    return NextResponse.json(data, { status: backendResponse.status });

  } catch (error) {
    console.error('[API PROXY REFRESH ESTIMATES] Error proxying to backend:', error);
    return NextResponse.json(
      { message: 'Error proxying request to backend' },
      { status: 500 }
    );
  }
} 