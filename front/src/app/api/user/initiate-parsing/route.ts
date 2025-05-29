import { type NextRequest, NextResponse } from 'next/server';

export async function POST(request: NextRequest) {
  try {
    const backendUrl = `${process.env.BACKEND_API_URL}/api/user/initiate-parsing`;

    const authToken = request.headers.get('Authorization');

    const headers: HeadersInit = {
    };
    if (authToken) {
      headers['Authorization'] = authToken;
    } else {
      return NextResponse.json({ message: 'Отсутствует токен авторизации для инициации парсинга' }, { status: 401 });
    }

    const backendResponse = await fetch(backendUrl, {
      method: 'POST',
      headers: headers,
    });

    let data;
    try {
      data = await backendResponse.json();
    } catch (e) {
      if (backendResponse.ok && backendResponse.status === 204) {
        data = { message: "Запрос на парсинг принят." };
      } else if (backendResponse.ok) {
        data = { message: "Ответ от сервера не в формате JSON, но запрос успешен." };
      } else {
      }
    }


    if (!backendResponse.ok) {
      const errorMessage = data?.message || 'Ошибка на стороне бэкенда при инициации парсинга.';
      return NextResponse.json({ message: errorMessage }, { status: backendResponse.status });
    }

    return NextResponse.json(data || { message: 'Парсинг успешно инициирован (или уже был выполнен).' }, { status: backendResponse.status });

  } catch (error) {
    console.error('[API INITIATE PARSING PROXY ERROR]', error);
    let message = 'Неизвестная ошибка сервера при попытке инициации парсинга';
    if (error instanceof Error) {
        message = error.message || message;
    }
    return NextResponse.json({ message }, { status: 500 });
  }
} 