import { NextRequest, NextResponse } from 'next/server';

export async function POST(request: NextRequest) {
  const authToken = request.headers.get('Authorization');

  if (!authToken) {
    return NextResponse.json({ message: 'Authorization token is missing' }, { status: 401 });
  }

  const { searchParams } = new URL(request.url);
  const date = searchParams.get('date');

  if (!date) {
    return NextResponse.json({ message: "Query parameter 'date' is missing" }, { status: 400 });
  }

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

    const contentType = backendResponse.headers.get("content-type");
    let data;
    if (contentType && contentType.includes("application/json")) {
        data = await backendResponse.json();
    } else {
        if (backendResponse.ok) {
            return new NextResponse(null, { status: backendResponse.status });
        }
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