import os
import json
import requests
import tempfile
import numpy as np
from com.chaquo.python import Python

def transcribe_audio(file_path, language, api_key):
    """
    Optimized transcription using Whisper API with preprocessing
    
    Args:
        file_path: Path to the audio file
        language: Language code (e.g., "english", "french")
        api_key: OpenAI API key
    
    Returns:
        Transcribed text
    """
    try:
        # Check if file exists and is accessible
        if not os.path.exists(file_path):
            return "Error: File not found"
            
        # Prep the API request
        headers = {
            "Authorization": f"Bearer {api_key}"
        }
        
        url = "https://api.openai.com/v1/audio/transcriptions"
        
        # Optimize file size if needed (simple implementation)
        optimized_path = optimize_audio_if_needed(file_path)
        file_to_send = optimized_path if optimized_path else file_path
        
        # Send the request with optimized timeouts
        with open(file_to_send, "rb") as audio_file:
            files = {
                "file": audio_file,
                "model": (None, "whisper-1"),
                "language": (None, language)
            }
            
            response = requests.post(
                url,
                headers=headers,
                files=files,
                timeout=60
            )
        
        # Clean up temp file if created
        if optimized_path and optimized_path != file_path:
            try:
                os.remove(optimized_path)
            except:
                pass
        
        # Process response
        if response.status_code == 200:
            return response.json().get("text", "")
        else:
            return f"API Error: {response.status_code} - {response.text}"
            
    except Exception as e:
        return f"Error: {str(e)}"

def optimize_audio_if_needed(file_path):
    """
    Optimize audio file if it's too large
    Simple implementation - returns original path if optimization not needed/possible
    """
    try:
        file_size = os.path.getsize(file_path)
        # Only optimize if file is larger than 25MB
        if file_size <= 25 * 1024 * 1024:
            return None
            
        # For larger files, we could implement compression here
        # For now, just return the original path as this is a placeholder
        return None
    except:
        return None