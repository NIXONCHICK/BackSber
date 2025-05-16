import { type NextRequest, NextResponse } from 'next/server';

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    // URL вашего реального бэкенд API
    const backendUrl = `${process.env.BACKEND_API_URL}/api/auth/register`;

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

    // При успешной регистрации бэкенд возвращает LoginResponse с токеном,
    // но обычно после регистрации пользователя перенаправляют на страницу входа.
    // Поэтому здесь мы просто возвращаем успешный ответ.
    // Если бэкенд возвращает токен и вы хотите автоматически логинить пользователя, 
    // то можно сохранить токен здесь, как и при логине.
    return NextResponse.json(data, { status: backendResponse.status });

  } catch (error) {
    console.error('[API REGISTER PROXY ERROR]', error);
    // Убедимся, что возвращаем объект с полем message, как ожидает фронтенд
    if (error instanceof Error) {
        return NextResponse.json({ message: error.message || 'Ошибка сервера при попытке регистрации' }, { status: 500 });
    }
    return NextResponse.json({ message: 'Неизвестная ошибка сервера при попытке регистрации' }, { status: 500 });
  }
} 