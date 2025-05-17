import { type NextRequest, NextResponse } from 'next/server';

export async function GET(request: NextRequest) {
  try {
    const backendUrl = `${process.env.BACKEND_API_URL}/api/semesters`;
    const authToken = request.headers.get('Authorization');

    const headers: HeadersInit = {};
    if (authToken) {
      headers['Authorization'] = authToken;
    } else {
      return NextResponse.json({ message: 'Отсутствует токен авторизации' }, { status: 401 });
    }

    const backendResponse = await fetch(backendUrl, {
      method: 'GET',
      headers: headers,
    });

    const data = await backendResponse.json();

    if (!backendResponse.ok) {
      return NextResponse.json(data || { message: 'Ошибка на стороне бэкенда при запросе семестров' }, { status: backendResponse.status });
    }

    return NextResponse.json(data, { status: backendResponse.status });

  } catch (error) {
    console.error('[API SEMESTERS PROXY ERROR]', error);
    let message = 'Неизвестная ошибка сервера при запросе семестров';
    if (error instanceof Error) {
        message = error.message || message;
    }
    return NextResponse.json({ message }, { status: 500 });
  }
} 