import { type NextRequest, NextResponse } from 'next/server';

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    // URL вашего реального бэкенд API
    const backendUrl = `${process.env.BACKEND_API_URL}/api/auth/login`;

    const backendResponse = await fetch(backendUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
    });

    const data = await backendResponse.json();

    if (!backendResponse.ok) {
      return NextResponse.json(data, { status: backendResponse.status });
    }

    // Мы можем захотеть установить cookie с токеном здесь
    // Например: response.cookies.set('token', data.token, { httpOnly: true, path: '/' });
    // Пока просто возвращаем данные, включая токен
    return NextResponse.json(data, { status: backendResponse.status });

  } catch (error) {
    console.error('[API LOGIN PROXY ERROR]', error);
    return NextResponse.json({ message: 'Ошибка сервера при попытке входа' }, { status: 500 });
  }
} 