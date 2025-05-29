import { type NextRequest, NextResponse } from 'next/server';

interface Params {
  subjectId: string;
}

export async function GET(request: NextRequest, { params }: { params: Params }) {
  try {
    const { subjectId } = params;
    if (!subjectId) {
      return NextResponse.json({ message: 'Отсутствует ID предмета' }, { status: 400 });
    }

    const numericSubjectId = parseInt(subjectId, 10);
    if (isNaN(numericSubjectId)) {
      return NextResponse.json({ message: 'ID предмета должен быть числом' }, { status: 400 });
    }

    const backendUrl = `${process.env.BACKEND_API_URL}/api/subjects/${numericSubjectId}/tasks`;
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
      return NextResponse.json(data || { message: 'Ошибка на стороне бэкенда при запросе задач для предмета' }, { status: backendResponse.status });
    }

    return NextResponse.json(data, { status: backendResponse.status });

  } catch (error) {
    console.error('[API TASKS BY SUBJECT PROXY ERROR]', error);
    let message = 'Неизвестная ошибка сервера при запросе задач для предмета';
    if (error instanceof Error) {
        message = error.message || message;
    }
    return NextResponse.json({ message }, { status: 500 });
  }
} 